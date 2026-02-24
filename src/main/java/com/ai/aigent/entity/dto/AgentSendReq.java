package com.ai.aigent.entity.dto;

import lombok.Data;

import java.util.List;
@Data
public class AgentSendReq {
    private String sessionId;
    private String message;
    private String messageId;
    private String token;
    private List<Long> attachments;  // 关键：附件数组
}
