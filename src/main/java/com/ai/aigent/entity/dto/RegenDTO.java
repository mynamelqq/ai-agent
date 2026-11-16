package com.ai.aigent.entity.dto;

import lombok.Data;
@Data
public class RegenDTO {
    private String chatId;
    private String assistantMessageId;
    private String userMessageId;
    private String token;
}
