package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class StreamingSnapshotDTO {
    private String chatId;
    private Long messageId;
    private String content;
    private String reasoning;
    private Integer seq;
    private String status;
    private Long updatedAt;
}