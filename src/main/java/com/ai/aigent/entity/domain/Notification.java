package com.ai.aigent.entity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 
 * @TableName notification
 */
@TableName(value ="notification")
@Data
public class Notification {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private String title;

    /**
     * 
     */
    private String content;

    /**
     * 
     */
    private String type;

    /**
     * 
     */
    private String link;

    /**
     * 
     */
    private LocalDateTime createdAt;

    /**
     * 
     */
    private Long createdBy;
}