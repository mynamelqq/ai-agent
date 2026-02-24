package com.ai.aigent.entity.domin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

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