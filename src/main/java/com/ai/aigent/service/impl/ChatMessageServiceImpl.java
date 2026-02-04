package com.ai.aigent.service.impl;

import com.ai.aigent.entity.ChatMessage;
import com.ai.aigent.mapper.ChatMessageMapper;
import com.ai.aigent.service.ChatMessageService;
import com.ai.aigent.utils.KryoUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
* @author MECHREVO
* @description 针对表【chat_message】的数据库操作Service实现
* @createDate 2025-12-19 21:36:04
*/
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
    implements ChatMessageService {
    @Override
    public void saveMessage(String conversationId, Message message) {
            ChatMessage entity = new ChatMessage();
            entity.setConversationId(conversationId);
            entity.setCreateTime(LocalDateTime.now());
            entity.setPayload(KryoUtils.serialize(message));
            entity.setHasAttachment(false);
            entity.setIsDeleted(0);
            entity.setMessageType(message.getMessageType().getValue());
            this.save(entity);
    }
    @Override
    public void saveMessages(String conversationId, List<Message> messages) {
         for (Message message : messages) {
            ChatMessage entity = new ChatMessage();
            entity.setIsDeleted(0);
            entity.setConversationId(conversationId);
            entity.setCreateTime(LocalDateTime.now());            entity.setHasAttachment(false);
            entity.setPayload(KryoUtils.serialize(message));
            entity.setMessageType(message.getMessageType().getValue());
            this.save(entity);
        }
    }
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Override
    public List<Message> loadRecentMessages(String conversationId, int lastN) {
        if (lastN <= 0) {
            return Collections.emptyList();
        }
      List<ChatMessage> entities = this.list(
        Wrappers.<ChatMessage>lambdaQuery()
            .eq(ChatMessage::getConversationId, conversationId)
            .select(ChatMessage::getPayload, ChatMessage::getCreateTime)
            .orderByDesc(ChatMessage::getCreateTime)
            .last("LIMIT " + lastN)
    );

    // ② 轻量 count
    long total = this.count(
        Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getIsDeleted,0)
            .eq(ChatMessage::getConversationId, conversationId)
    );

    // ③ 达到阈值才发 MQ（而且后面会加幂等）
    if (total >=lastN) {
        rabbitTemplate.convertAndSend(
                  "summaryChatExchange",  // Exchange 名称
            "summary",
            conversationId
        );
    }
        // （从旧到新）
    entities.sort(Comparator.comparing(ChatMessage::getCreateTime));
    return entities.stream()
                   .map(this::deserialize)
                   .toList();
    }
    @Override
    public long countByConversationId(String conversationId) {
        return this.count(
            Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
        );
    }
     public Message deserialize(ChatMessage entity) {
        try {
            byte[] payload = entity.getPayload();
            if (payload == null || payload.length == 0) {
                throw new IllegalStateException("payload is empty");
            }
            return KryoUtils.deserialize(payload);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

}




