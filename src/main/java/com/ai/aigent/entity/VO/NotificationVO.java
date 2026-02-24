package com.ai.aigent.entity.VO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationVO {

    private Long id;

    private String title;

    private String content;

    private String type;

    private String link;

    private Boolean isRead;

    private LocalDateTime time;
}