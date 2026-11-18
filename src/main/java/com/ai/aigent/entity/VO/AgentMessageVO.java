package com.ai.aigent.entity.VO;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.Date;

@Data
public class AgentMessageVO {
    private String id;
    private String type;
    private String payload;
    private Date createdAt;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long runId;
    private Integer cost;
    private Integer tokens;
    private Integer thinkingTime;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer cachedTokens;
}