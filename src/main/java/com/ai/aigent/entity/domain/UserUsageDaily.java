package com.ai.aigent.entity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 
 * @TableName user_usage_daily
 */
@TableName(value ="user_usage_daily")
@Data
public class UserUsageDaily {
    @TableId(type = IdType.AUTO)
    private String id;
    private String userId;
    private String type;
    private Long token;
    private LocalDateTime time;
    private Integer promptTokens;
    private Integer cachedTokens;
    private String chatModel;
    private String fromWhere;//来自对话还是智能体
    private Integer completionTokens;
}