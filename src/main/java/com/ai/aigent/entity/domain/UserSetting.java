package com.ai.aigent.entity.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 
 * @TableName user_setting
 */
@TableName(value ="user_setting")
@Data
public class UserSetting {
    @TableId
    private String userId;
    private String chatModel;
    private Integer contextWindow;
    private Boolean useMcp;
    private Integer topk;
    private String reasoningEffort;
    private Double frequencyPenalty;
    private String toolMode;
    private Integer maxTokens;
    private Boolean chatMemory;
    private String colorPoint;
    private Double temperature;
    private Boolean foldingThinking;
    private Boolean isSummary;
    private String tone;
    private Boolean isExtend;
}