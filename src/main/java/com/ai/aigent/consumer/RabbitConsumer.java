package com.ai.aigent.consumer;

import cn.hutool.json.JSONUtil;
import com.ai.aigent.service.impl.SummaryBatchService;
import com.ai.aigent.utils.KryoUtils;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RabbitConsumer {
    private static final String SUMMARY_PROMPT = """
            你是对话摘要器。只为本批新消息生成一段长期记忆摘要，保留重要事实、偏好和结论，
            不重复已有摘要，不添加对话中不存在的信息。
            已有长期记忆：
            {previousSummary}
            本批消息：
            {messages}
            """;

    private final OpenAiChatModel model;
    private final SummaryBatchService batches;
    private final SummaryPublisher publisher;

    public RabbitConsumer(@Qualifier("openAiChatModel") OpenAiChatModel model,
                          SummaryBatchService batches, SummaryPublisher publisher) {
        this.model = model;
        this.batches = batches;
        this.publisher = publisher;
    }

    @RabbitListener(queues = "summaryChat", containerFactory = "summaryRabbitListenerContainerFactory")
    public void receiveMessage(String body, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        // 手动 ACK：只有数据库成功，或重试/失败消息确认转存后，才确认原消息。
        try {
            SummaryChatEvent event = parseEvent(body);
            if (event == null) {
                // 旧格式没有持久 batchId，无法安全地映射到固定的 10 条，先转失败队列待人工处理。
                publisher.publishRawConfirmed(RabbitMQConfig.DEAD_EXCHANGE,
                        RabbitMQConfig.DEAD_KEY, body, "legacy-summary");
                channel.basicAck(deliveryTag, false);
                return;
            }

            SummaryBatchService.Claim claim = batches.claim(event);
            switch (claim.decision()) {
                // 已成功的批次再次投递时不再调用模型，也不重复追加摘要。
                case DONE -> channel.basicAck(deliveryTag, false);
                // 前序批次尚未完成，或本批已有有效租约：延迟 30 秒再尝试。
                case RETRY_LATER -> forwardAndAck(event, RabbitMQConfig.RETRY_EXCHANGE,
                        RabbitMQConfig.RETRY_KEY, channel, deliveryTag);
                // 已达到模型尝试上限，或前序批次失败：转入待人工处理的队列。
                case FINAL_FAILURE -> forwardAndAck(event, RabbitMQConfig.DEAD_EXCHANGE,
                        RabbitMQConfig.DEAD_KEY, channel, deliveryTag);
                // 仅 CLAIMED 分支读取固定成员并调用模型。
                case CLAIMED -> processClaim(event, claim, channel, deliveryTag);
            }
        } catch (Exception e) {
            // 领取状态或转发消息失败时保留原消息；数据库长期不可用时这里仍可能反复重投。
            log.error("Summary consumption failed before a safe ACK", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private SummaryChatEvent parseEvent(String body) {
        // 旧版 conversationId 或 messageIds 消息缺少持久 batchId，不在新流程中重新分配批次。
        try {
            var json = JSONUtil.parseObj(body);
            String batchId = json.getStr("batchId");
            String conversationId = json.getStr("conversationId");
            return batchId != null && !batchId.isBlank()
                    && conversationId != null && !conversationId.isBlank()
                    ? new SummaryChatEvent(batchId, conversationId) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void processClaim(SummaryChatEvent event, SummaryBatchService.Claim claim,
                              Channel channel, long deliveryTag) throws IOException {
        // claim() 的短事务已结束；反序列化和外部模型调用都不持有会话行锁。
        try {
            List<SummaryBatchService.BatchMessage> messages = batches.loadMessages(claim);
            if (messages.size() != 10) throw new IllegalStateException("Summary batch must contain 10 messages");
            StringBuilder input = new StringBuilder();
            for (SummaryBatchService.BatchMessage row : messages) {
                Message message = KryoUtils.deserialize(row.payload());
                input.append(formatMessage(message)).append('\n');
            }
            String previous = batches.currentSummary(event.conversationId());
            Prompt prompt = new PromptTemplate(SUMMARY_PROMPT).create(Map.of(
                    "previousSummary", previous == null ? "" : previous,
                    "messages", input.toString()));
            ChatResponse response = model.call(prompt);
            String summary = response.getResult().getOutput().getText();
            if (summary == null || summary.isBlank()) throw new IllegalStateException("Empty summary response");
            // complete() 会再次核对租约令牌，并在一个短事务中提交摘要及归档状态。
            batches.complete(claim, summary.trim());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.warn("Summary generation failed for batch {}", event.batchId(), e);
            // fail() 写回重试次数及状态；转发成功前不能 ACK 原消息。
            SummaryBatchService.Decision next = batches.fail(claim, e);
            if (next == SummaryBatchService.Decision.FINAL_FAILURE) {
                forwardAndAck(event, RabbitMQConfig.DEAD_EXCHANGE,
                        RabbitMQConfig.DEAD_KEY, channel, deliveryTag);
            } else {
                forwardAndAck(event, RabbitMQConfig.RETRY_EXCHANGE,
                        RabbitMQConfig.RETRY_KEY, channel, deliveryTag);
            }
        }
    }

    private void forwardAndAck(SummaryChatEvent event, String exchange, String routingKey,
                               Channel channel, long deliveryTag) throws IOException {
        // 先确认目标队列已接收，再 ACK 原消息，避免转发失败时把任务丢掉。
        publisher.publishConfirmed(exchange, routingKey, event);
        channel.basicAck(deliveryTag, false);
    }

    private String formatMessage(Message message) {
        String role = message instanceof SystemMessage ? "system"
                : message instanceof UserMessage ? "user"
                : message instanceof AssistantMessage ? "assistant" : "unknown";
        return role + ": " + message.getText();
    }
}
