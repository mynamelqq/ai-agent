package com.ai.aigent.consumer;

import com.ai.aigent.constant.AiCientConstans;
import com.ai.aigent.entity.domin.ChatMessage;
import com.ai.aigent.service.ChatMessageService;
import com.ai.aigent.service.ChatSummaryService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RabbitConsumer {
    private final static String cueWord = "你是一个对话摘要器。\n" +
            "\n" +
            "请将以下对话内容总结为一段“长期记忆摘要”，要求：\n" +
            "1. 保留重要事实、用户偏好、已达成的结论\n" +
            "2. 忽略寒暄、重复和无关信息\n" +
            "3. 使用第三人称、陈述句\n" +
            "4. 不要添加对话中不存在的信息\n" +
            "5. 输出为一段连续文本，不要分点\n" +
            "\n" +
            "对话内容：\n" +
            "{messages}";

    @Resource
    @Qualifier("openAiChatModel")
    private OpenAiChatModel openAiChatModel;
    @Resource
    private ChatSummaryService chatSummaryService;
    @Resource
    private ChatMessageService chatMessageService;
    @RabbitListener(queues = "summaryChat")
    @Transactional
    public void receiveMessage(String message) {//发送id序列
        String id=message;
        //从数据库中查出要摘要的10条然后拼到strs中，需要加上messageType以告知是谁说的
        List<ChatMessage> oldestMessages = chatMessageService.list(
        Wrappers.<ChatMessage>lambdaQuery()
            .eq(ChatMessage::getConversationId, id)
                .eq(ChatMessage::getIsDeleted,0)
            .orderByAsc(ChatMessage::getCreateTime)//升序排序
            .last("LIMIT "+ AiCientConstans.SHORT_MEMORY_LENGTH)
         );
        if (oldestMessages.size()<AiCientConstans.SHORT_MEMORY_LENGTH) {
            return;
        }
        StringBuilder strs = new StringBuilder();

       for (ChatMessage entity : oldestMessages) {
            Message msg =chatMessageService.deserialize(entity);
            entity.setIsDeleted(1);
            strs.append(formatMessage(msg))
                .append("\n");
       }
       chatMessageService.updateBatchById(oldestMessages);
        PromptTemplate promptTemplate =
                new PromptTemplate(cueWord);
        Prompt prompt = promptTemplate.create(Map.of("messages", strs));
        ChatResponse call = openAiChatModel.call(prompt);

        chatSummaryService.updateSummary(id,call.getResult().getOutput().getText());//更新摘要

    }

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private VectorStore codeAppVectorStore;
//    @Transactional
//    @RabbitListener(queues = "ChatVectorStoreQueue")
//    public void receiveMessageFromVectorStore(String  message) {
//        ChatEmbeddingDTO dto = JSONUtil.toBean(message, ChatEmbeddingDTO.class, true);
//         try {
//            // 1️⃣ 拼接 Q + A（向量内容）
//             String content = buildContent(dto.getUserMessage(), dto.getAssistantMessage());
//            if (StringUtils.isBlank(dto.getUserId())) {
//                dto.setUserId(RandomUtil.randomNumbers(10));
//            }
//            List<String> chunks = splitByTokens(content);
//            for (String chunk : chunks) {
//                String documentId = UUID.randomUUID().toString();
//                Document document = new Document(documentId, chunk, Map.of());
//                codeAppVectorStore.add(List.of(document));
//                jdbcTemplate.update(
//                    "insert into chat_memory_vector (id, user_id, conservation_id, memory_type, created_at, is_deleted) values (?, ?, ?, ?, now(), false)",
//                    documentId,
//                    Long.parseLong(dto.getUserId()),
//                    dto.getChatId(),
//                    dto.getMemoryType()
//                );
//
//                log.info("Vector saved: userId={}, sessionId={}, documentId={}",
//                        dto.getUserId(), dto.getChatId(), documentId);
//            }
//            } catch (Exception e) {
//                log.error("ChatVectorStore 消费失败: {}", dto, e);
//            }
//    }

    private List<String> splitByTokens(String content) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;

        for (String line : content.split("\n")) {
            int lineTokens = line.length();
            if (currentTokens + lineTokens > AiCientConstans.MAX_LENGTH_EMBEDDING) {
                chunks.add(current.toString());
                current = new StringBuilder();
                currentTokens = 0;
            }
            current.append(line).append("\n");
            currentTokens += lineTokens;
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

     private String buildContent(String userMsg, String aiMsg) {
        return "Q: " + userMsg + "\nA: " + aiMsg;
    }


    private String formatMessage(Message message) {

        String role;

        if (message instanceof SystemMessage) {
            role = "system";
        } else if (message instanceof UserMessage) {
            role = "user";
        } else if (message instanceof AssistantMessage) {
            role = "assistant";
        }  else {
            role = "unknown";
        }

        return role + ": "+message.getText();
    }
}
