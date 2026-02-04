package com.ai.aigent.entity.dto;

import lombok.Data;

import java.util.List;
@Data
public class WebSearchResponseDTO {

    private boolean success;
    private List<WebSearchResultDTO> results;
    private String error;

    public static WebSearchResponseDTO ok(List<WebSearchResultDTO> results) {
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