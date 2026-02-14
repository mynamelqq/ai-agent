package com.ai.aigent.service;

import com.ai.aigent.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
* @author MECHREVO
* @description 针对表【chat_message】的数据库操作Service
* @createDate 2025-12-19 21:14:32
*/

public interface ChatMessageService extends IService<ChatMessage> {

     void saveAssistantMessage(String conversationId,String message);
     void updateMessage(String conversationId,String message,String messageId,String type);
     void saveMessage(String conversationId, Message message,String messageId);
     void saveMessages(String conversationId, List<Message> messages);
     List<Message> loadRecentMessages(String conversationId, int lastN);
     long countByConversationId(String conversationId);
     public Message deserialize(ChatMessage entity);
}
