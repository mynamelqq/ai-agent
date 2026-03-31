package com.ai.aigent.entity.dto;

import lombok.Data;
@Data
public class WebSearchResponseDTO {

    private boolean success;
    private String results;
    private String error;

    public static WebSearchResponseDTO ok(String results) {
        WebSearchResponseDTO dto = new WebSearchResponseDTO();
        dto.success = true;
        dto.results = results;
        return dto;
    }

    public static WebSearchResponseDTO error(String msg) {
        WebSearchResponseDTO dto = new WebSearchResponseDTO();
        dto.success = false;
        dto.error = msg;
        return dto;
    }

    // getters / setters
}