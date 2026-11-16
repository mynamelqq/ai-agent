package com.ai.aigent.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class SendBatchNotificationRequest {

    private List<Long> userIds;

    private String title;

    private String content;

    private String type;

    private String link;

}