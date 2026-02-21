package com.ai.aigent.service.impl;

import com.ai.aigent.entity.domin.ChatMessage;
import com.ai.aigent.entity.dto.ChatSendReq;
import com.ai.aigent.mapper.ChatMessageMapper;
import com.ai.aigent.service.ChatMessageService;
import com.ai.aigent.utils.KryoUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
    public void saveAssistantMessage(String conversationId, String message, Integer token,Integer cost,
     Integer promptTokens,  Integer completionTokens,Integer cachedTokens
    ) {
            AssistantMessage build = AssistantMessage.builder().content(message).build();
            ChatMessage entity = new ChatMessage();
            entity.setConversationId(conversationId);
            entity.setCreateTime(LocalDateTime.now());
            entity.setPayload(KryoUtils.serialize(build));
            entity.setIsDeleted(0);entity.setToken(token);entity.setCost(cost);
            entity.setPromptTokens(promptTokens);
            entity.setCompletionTokens(completionTokens);
            entity.setCachedTokens(cachedTokens);
            entity.setIsImage(false);
            entity.setIsGenerate(false);
            entity.setIsThinking(false);
            entity.setIsEcharts(false);
            entity.setIsSearch(false);
            entity.setMessageType("assistant");
            this.save(entity);
    }
    @Override
    public void updateMessage(String conversationId, String message, String messageId, String type, Integer token,Integer cost,
                              Integer promptTokens,  Integer completionTokens,Integer cachedTokens) {
        if(type=="user"){
            this.lambdaUpdate().eq(ChatMessage::getId, messageId)
                .set(ChatMessage::getPayload, KryoUtils.serialize(new UserMessage(message)))
                    .update();
        }
        else{
            this.lambdaUpdate().eq(ChatMessage::getId, messageId)
                .set(ChatMessage::getPayload, KryoUtils.serialize(new AssistantMessage(message)))
                    .set(ChatMessage::getCost,cost)
                    .setSql("prompt_tokens = prompt_tokens + "+promptTokens)
                    .setSql("completion_tokens = completion_tokens + "+completionTokens)
                    .setSql("cached_tokens = cached_tokens + "+cachedTokens)
                    .setSql("token = token + "+ token)
                    .update();
        }

    }

    @Override
    public void saveMessage(ChatSendReq chatSendReq) {
            ChatMessage entity = new ChatMessage();
            entity.setConversationId(chatSendReq.getChatId());
            entity.setCreateTime(LocalDateTime.now());
            entity.setId(Long.parseLong(chatSendReq.getMessageId()));
            entity.setPayload(KryoUtils.serialize(new UserMessage(chatSendReq.getMessage())));
            entity.setIsDeleted(0);entity.setToken(0);
            entity.setIsEcharts(chatSendReq.getIsEcharts());
            entity.setIsSearch(chatSendReq.getIsSearch());
            entity.setIsImage(chatSendReq.getIsImageGen());
            entity.setIsThinking(chatSendReq.getIsThinking());
            entity.setIsGenerate(chatSendReq.getIsGenerateFile());
            entity.setMessageType("user");
            this.save(entity);
    }
    @Override
    public void saveMessages(String conversationId, List<Message> messages) {
         for (Message message : messages) {
            ChatMessage entity = new ChatMessage();
            entity.setIsDeleted(0);
            entity.setConversationId(conversationId);
            entity.setCreateTime(LocalDateTime.now());
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




