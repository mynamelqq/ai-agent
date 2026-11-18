package com.ai.aigent.entity.dto;

import lombok.Data;

import java.util.List;
@Data
public class AgentSendReq {
    private String sessionId;
    private String message;
    private String messageId;
    // 前端为每一轮生成，使用十进制整数字符串传输以避免 JS 精度丢失。
    private Long runId;
    private String token;
    private List<Long> attachments;  // 关键：附件数组
}
