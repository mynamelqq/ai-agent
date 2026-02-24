package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class TimeResponseDTO {
    private boolean success;
    private String content;
        public static TimeResponseDTO ok(String content) {
        TimeResponseDTO dto = new TimeResponseDTO();
        dto.success = true;
        dto.content =content;
        return dto;
    }
}
