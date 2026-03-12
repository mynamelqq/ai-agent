package com.ai.aigent.entity.domin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.apache.ibatis.type.ByteArrayTypeHandler;

import java.time.LocalDateTime;

/**
 * 
 * @TableName chat_message
 */
@TableName(value ="chat_message")
@Data
public class ChatMessage {
    /**
     * 
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Integer thinkingTime;
    /**
     * 
     */
    private String conversationId;
    /**
     * 
     */
    private String messageType;
    @TableField(typeHandler = ByteArrayTypeHandler.class)
    private byte[] payload;
    private Integer isDeleted;
    private Boolean isThinking;
    private Boolean isImage;
    private Boolean isGenerate;
    private Boolean isEcharts;
    private Integer cost;
    private Integer promptTokens;
    private Integer cachedTokens;
    private Integer completionTokens;
    private Integer token;
    private Boolean isFavor;
    private Boolean isSearch;
    private LocalDateTime createTime;
}