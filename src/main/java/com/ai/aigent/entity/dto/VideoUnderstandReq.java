package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class VideoUnderstandReq {
    private String videoUrl;

    private String prompt;
    private String model;
    private Integer maxTokens;
    private Double temperature;
}
