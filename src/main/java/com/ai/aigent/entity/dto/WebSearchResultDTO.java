package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class WebSearchResultDTO {

    private String title;
    private String url;
    private String snippet;

    public WebSearchResultDTO() {}

    public WebSearchResultDTO(String title, String url, String snippet) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
    }

    // getters / setters
}