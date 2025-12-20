package com.ai.aigent.app;

import com.ai.aigent.entity.ChatMessage;
import com.ai.aigent.service.ChatMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
public class JdbcChatMemory implements ChatMemory {

    @Resource
    ChatMessageService chatMessageService;

    @Override
    public void add(String conversationId, Message message) {
         chatMessageService.saveMessage(conversationId, message);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
         if (messages == null || messages.isEmpty()) {
                return;
            }
        chatMessageService.saveMessages(conversationId, messages);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        return chatMessageService.loadRecentMessages(conversationId,lastN);
    }

    @Override
    public void clear(String conversationId) {
        LambdaQueryWrapper<ChatMessage> lambdaQueryWrapper=new LambdaQueryWrapper<ChatMessage>();
        lambdaQueryWrapper.eq(ChatMessage::getConversationId,conversationId);
        chatMessageService.remove(lambdaQueryWrapper);
    }
}
