package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class BochaWebSearchRequest {
    private String query;
    private String freshness = "noLimit";
    private Boolean summary = false;
    private Integer count = 10;
}
