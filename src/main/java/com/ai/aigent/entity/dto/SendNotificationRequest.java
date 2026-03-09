package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class SendNotificationRequest {

    private Long userId;

    private String title;

    private String content;

    private String type;

    private String link;

}