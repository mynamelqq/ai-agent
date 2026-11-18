package com.ai.aigent.tools;

import com.ai.aigent.entity.dto.WebPageFetchResponseDTO;
import com.ai.aigent.utils.FirecrawlClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class WebScrapingTool {
    private static final int MAX_CHARS = 6000;
    private final FirecrawlClient client;
    private final ObjectMapper objectMapper;

    public WebScrapingTool(FirecrawlClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "使用 Firecrawl 抓取指定网页的正文 Markdown 内容")
    public String fetchWebPage(@ToolParam(description = "要抓取的网页 URL") String url) {
        if (url == null || url.isBlank()) {
            return toJson(WebPageFetchResponseDTO.error(url, "url 不能为空"));
        }
        try {
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return toJson(WebPageFetchResponseDTO.error(url, "仅支持 HTTP 或 HTTPS URL"));
            }
            JsonNode data = client.scrape(Map.of(
                    "url", url,
                    "formats", List.of("markdown"),
                    "onlyMainContent", true));
            String content = data.path("markdown").asText("").trim();
            if (content.isEmpty()) {
                return toJson(WebPageFetchResponseDTO.error(url, "Firecrawl 未返回网页正文"));
            }
            if (content.length() > MAX_CHARS) {
                content = content.substring(0, MAX_CHARS) + "...";
            }
            return toJson(WebPageFetchResponseDTO.ok(url, content));
        } catch (Exception e) {
            return toJson(WebPageFetchResponseDTO.error(url, e.getMessage()));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON序列化失败\"}";
        }
    }
}
