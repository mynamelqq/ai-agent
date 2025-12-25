package com.ai.aigent.consumer;

import com.ai.aigent.AiCientConstans;
import com.ai.aigent.entity.ChatMessage;
import com.ai.aigent.service.ChatMessageService;
import com.ai.aigent.service.ChatSummaryService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
@Service
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
    @Qualifier("dashscopeChatModel")
    private ChatModel dashscopeChatModel;
    @Resource
    private ChatSummaryService chatSummaryService;
    @Resource
    private ChatMessageService chatMessageService;
    @RabbitListener(queues = "summaryChat")
    public void receiveMessage(String message) {//发送id序列
        String id=message;
        //从数据库中查出要摘要的10条然后拼到strs中，需要加上messageType以告知是谁说的
        List<ChatMessage> oldestMessages = chatMessageService.list(
        Wrappers.<ChatMessage>lambdaQuery()
            .eq(ChatMessage::getConversationId, id)
            .orderByAsc(ChatMessage::getCreateTime)//升序排序
            .last("LIMIT "+ AiCientConstans.SHORT_MEMORY_LENGTH)
         );
        if (oldestMessages.size()<6) {
            return;
        }
        StringBuilder strs = new StringBuilder();

       for (ChatMessage entity : oldestMessages) {
            Message msg =chatMessageService.deserialize(entity);

            strs.append(formatMessage(msg))
                .append("\n");
       }
        PromptTemplate promptTemplate =
                new PromptTemplate(cueWord);
        Prompt prompt = promptTemplate.create(Map.of("messages", strs));
        ChatResponse call = dashscopeChatModel.call(prompt);
        chatSummaryService.updateSummary(id,call.getResult().getOutput().getText());//更新摘要
        chatMessageService.removeByIds(
                oldestMessages.stream()
                    .map(ChatMessage::getId)
                    .toList()
        );

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
