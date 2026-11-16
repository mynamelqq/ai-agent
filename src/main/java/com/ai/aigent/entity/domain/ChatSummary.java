package com.ai.aigent.entity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 
 * @TableName chat_summary
 */
@TableName(value ="chat_summary")
@Data
public class ChatSummary {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private String conversationId;

    /**
     * 
     */
    private String summary;

    /**
     * 
     */

    private LocalDateTime updateTime;
}