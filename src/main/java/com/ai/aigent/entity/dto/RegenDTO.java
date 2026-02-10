package com.ai.aigent.entity.dto;

import lombok.Data;

import java.util.List;
@Data
public class RegenDTO {
    private String chatId;
    private String assistantMessage;
    private List<Long> attachments;  // 关键：附件数组
    private String agentId;          // 预留
    private String mode;             // chat / agent 预留
    private String token;
    private Boolean isThinking;
}
