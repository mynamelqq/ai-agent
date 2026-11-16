package com.ai.aigent.service;

import com.ai.aigent.entity.domain.ChatMessage;
import com.ai.aigent.entity.dto.ChatSendReq;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
* @author MECHREVO
* @description 针对表【chat_message】的数据库操作Service
* @createDate 2025-12-19 21:14:32
*/

public interface ChatMessageService extends IService<ChatMessage> {

     void saveAssistantMessage(String id,String conversationId,String message,Integer token,Integer cost,
     Integer promptTokens,  Integer completionTokens,Integer cachedTokens,Long thinkingTime);
     void updateMessage(String conversationId,String message,String messageId,String type,
                        Integer token,Integer cost
             ,Integer promptTokens,Integer completionTokens,Integer cachedTokens,Long thinkingTime);
     void saveMessage(ChatSendReq chatSendReq);
     void saveMessages(String conversationId, List<Message> messages);
     List<Message> loadRecentMessages(String conversationId, int lastN);
     long countByConversationId(String conversationId);
     Message deserialize(ChatMessage entity);
     void updateAssistantMessage(String id,String conversationId, String message, Integer token,Integer cost,
                                       Integer promptTokens,  Integer completionTokens,Integer cachedTokens,Long thinkingTime);

}
