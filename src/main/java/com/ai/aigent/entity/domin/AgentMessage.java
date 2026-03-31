package com.ai.aigent.entity.domin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 
 * @TableName agent_message
 */
@TableName(value ="agent_message")
@Data
public class AgentMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String type;
    private Integer tokens;
    private Date createdAt;
    private String payload;
    private Integer cost;
    private Integer thinkingTime;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer cachedTokens;
}