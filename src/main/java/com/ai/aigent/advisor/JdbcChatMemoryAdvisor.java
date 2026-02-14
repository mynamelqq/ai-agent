package com.ai.aigent.advisor;

import com.ai.aigent.constant.AiCientConstans;
import com.ai.aigent.entity.ChatMessage;
import com.ai.aigent.service.ChatMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public  class JdbcChatMemoryAdvisor implements ChatMemory {

    @Resource
    ChatMessageService chatMessageService;

    @Override
    public void add(String conversationId, Message message) {
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
    }

    @Override
    public List<Message> get(String conversationId) {
        return chatMessageService.loadRecentMessages(conversationId, AiCientConstans.SHORT_MEMORY_LENGTH);
    }


//    @Override
//    public List<Message> get(String conversationId, int lastN) {
//        return chatMessageService.loadRecentMessages(conversationId,lastN);
//    }

    @Override
    public void clear(String conversationId) {
        LambdaQueryWrapper<ChatMessage> lambdaQueryWrapper=new LambdaQueryWrapper<ChatMessage>();
        lambdaQueryWrapper.eq(ChatMessage::getConversationId,conversationId);
        chatMessageService.remove(lambdaQueryWrapper);
    }
}
