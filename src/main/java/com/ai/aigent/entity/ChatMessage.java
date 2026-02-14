package com.ai.aigent.entity;

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
    private LocalDateTime createTime;
}