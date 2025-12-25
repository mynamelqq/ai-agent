package com.ai.aigent.entity;

import com.baomidou.mybatisplus.annotation.*;
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
    private Boolean hasAttachment;
    private byte[]attachment;
    /**
     * 
     */
    @TableLogic
    @TableField(value = "is_deleted")
    private Integer isDeleted;
    private LocalDateTime createTime;
}