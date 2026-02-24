package com.ai.aigent.entity.VO;

import lombok.Data;

import java.util.Date;

@Data
public class AgentMessageVO {
    private String id;
    private String type;
    private String payload;
    private Date createdAt;
    private Integer cost;
    private Integer tokens;
    private Integer thinkingTime;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer cachedTokens;
}