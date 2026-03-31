package com.ai.aigent.entity.domin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 
 * @TableName user_notification
 */
@TableName(value ="user_notification")
@Data
public class UserNotification {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private Long userId;

    /**
     * 
     */
    private Long notificationId;

    /**
     * 
     */
    private Boolean isRead;

    /**
     * 
     */
    private LocalDateTime readTime;

    /**
     * 
     */
    private LocalDateTime createdAt;
}