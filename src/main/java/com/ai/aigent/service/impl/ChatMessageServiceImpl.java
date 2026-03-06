package com.ai.aigent.service.impl;

import com.ai.aigent.entity.domin.ChatMessage;
import com.ai.aigent.entity.domin.Conversation;
import com.ai.aigent.entity.domin.UserSetting;
import com.ai.aigent.entity.dto.ChatSendReq;
import com.ai.aigent.mapper.ChatMessageMapper;
import com.ai.aigent.service.ChatMessageService;
import com.ai.aigent.service.ConversationService;
import com.ai.aigent.service.UserSettingService;
import com.ai.aigent.utils.KryoUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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
     Integer promptTokens,  Integer completionTokens,Integer cachedTokens,Long thinkingTime
    ) {
            AssistantMessage build = AssistantMessage.builder().content(message).build();
            ChatMessage entity = new ChatMessage();
            entity.setConversationId(conversationId);
            entity.setThinkingTime(thinkingTime.intValue());
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
                              Integer promptTokens,  Integer completionTokens,Integer cachedTokens,
                                         Long thinkingTime) {
        if(type=="user"){
            this.lambdaUpdate().eq(ChatMessage::getId, messageId)
                .set(ChatMessage::getPayload, KryoUtils.serialize(new UserMessage(message)))
                    .update();
        }
        else{
            this.lambdaUpdate().eq(ChatMessage::getId, messageId)
                .set(ChatMessage::getPayload, KryoUtils.serialize(new AssistantMessage(message)))
                    .set(ChatMessage::getCost,cost)
                    .set(ChatMessage::getThinkingTime,thinkingTime.intValue())
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
    @Autowired
    private ConversationService conversationService;
    @Autowired
    private UserSettingService userSettingService;
    public List<Message> loadRecentMessages(String conversationId, int lastN) {
        if (lastN <= 0) {
            return Collections.emptyList();
        }
        Conversation conversation = conversationService.getById(conversationId);
        if(conversation==null){
            return Collections.emptyList();
        }
        String userId = conversation.getUser_id();
        UserSetting userSetting = userSettingService.getById(userId);//只获取不创建
        if(userSetting!=null){
            lastN=userSetting.getContextWindow();
        }
        // ① 先查出最新一条的 createTime
        ChatMessage latest = this.getOne(
            Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT 1")
        );
        if (latest == null) {
            return Collections.emptyList();
        }
        // ② 排除最新那条，再取 lastN 条
        List<ChatMessage> entities = this.list(
            Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .ne(ChatMessage::getId, latest.getId()) // 用主键排除
                .select(ChatMessage::getPayload, ChatMessage::getCreateTime)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT " + lastN)
        );
        if (userSetting!=null) {//有登录才创建
             long total = this.count(
                Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getIsDeleted, 0)
                    .eq(ChatMessage::getConversationId, conversationId)
            );
            if(total >= lastN){
                 if(userSetting.getIsSummary()) {
                rabbitTemplate.convertAndSend("summaryChatExchange", "summary", conversationId);
                }
            }
        }
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




