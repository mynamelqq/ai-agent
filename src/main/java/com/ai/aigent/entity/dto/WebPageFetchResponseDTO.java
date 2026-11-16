package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class WebPageFetchResponseDTO {
    private boolean success;
    private String url;
    private String content;
    private String error;

    public static WebPageFetchResponseDTO ok(String url, String content) {
        WebPageFetchResponseDTO dto = new WebPageFetchResponseDTO();
        dto.success = true;
        dto.url = url;
        dto.content = content;
        return dto;
    }

    public static WebPageFetchResponseDTO error(String url, String error) {
        WebPageFetchResponseDTO dto = new WebPageFetchResponseDTO();
        dto.success = false;
        dto.url = url;
        dto.error = error;
        return dto;
    }
}
