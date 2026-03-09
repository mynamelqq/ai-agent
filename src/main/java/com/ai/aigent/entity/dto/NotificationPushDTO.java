package com.ai.aigent.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationPushDTO {

    private String title;

    private String content;

    private String type;

    private String link;

    private Long unreadCount;
}