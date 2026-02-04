package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class ChatEmbeddingDTO {
    private String userMessage;
    private String assistantMessage;
    private String chatId;
    private String userId;
    private String memoryType;     // session / user_profile / agent
}
