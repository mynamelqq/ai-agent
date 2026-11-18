package com.ai.aigent.tools;

import com.ai.aigent.entity.dto.WebSearchResponseDTO;
import com.ai.aigent.utils.FirecrawlClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class WebSearchTool {
    private final FirecrawlClient client;
    private final ObjectMapper objectMapper;

    public WebSearchTool(FirecrawlClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "web_search", description = "使用 Firecrawl 搜索网页，返回标题、描述和链接", returnDirect = false)
    public String webSearch(
            @ToolParam(description = "搜索关键词，例如：阿里巴巴2024年ESG报告") String query,
            @ToolParam(description = "时间范围：noLimit, oneDay, oneWeek, oneMonth, oneYear") String freshness,
            @ToolParam(description = "是否返回结果描述 true/false") Boolean summary,
            @ToolParam(description = "返回条数，1-20") Integer count) {
        if (query == null || query.isBlank()) {
            return toJson(WebSearchResponseDTO.error("搜索关键词不能为空"));
        }
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("query", query);
            request.put("limit", count == null ? 5 : Math.max(1, Math.min(count, 20)));
            String tbs = switch (freshness == null ? "noLimit" : freshness) {
                case "oneDay" -> "qdr:d";
                case "oneWeek" -> "qdr:w";
                case "oneMonth" -> "qdr:m";
                case "oneYear" -> "qdr:y";
                default -> null;
            };
            if (tbs != null) request.put("tbs", tbs);

            JsonNode web = client.search(request).path("web");
            if (!web.isArray() || web.isEmpty()) {
                return toJson(WebSearchResponseDTO.ok("未搜索到相关结果。"));
            }
            StringBuilder results = new StringBuilder("搜索结果：\n\n");
            int index = 1;
            for (JsonNode item : web) {
                results.append(index++).append(". ").append(item.path("title").asText("无标题"))
                        .append("\n   链接：").append(item.path("url").asText(""));
                if (!Boolean.FALSE.equals(summary)) {
                    String description = item.path("description").asText("");
                    if (description.isBlank()) description = item.path("metadata").path("description").asText("");
                    if (!description.isBlank()) results.append("\n   摘要：").append(description);
                }
                results.append("\n\n");
            }
            return toJson(WebSearchResponseDTO.ok(results.toString()));
        } catch (Exception e) {
            return toJson(WebSearchResponseDTO.error("搜索失败: " + e.getMessage()));
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
