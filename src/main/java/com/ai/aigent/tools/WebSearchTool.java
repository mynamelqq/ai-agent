package com.ai.aigent.tools;

import com.ai.aigent.entity.dto.WebSearchResponseDTO;
import com.ai.aigent.entity.dto.WebSearchResultDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class WebSearchTool {

    private final String API_KEY = "YJUPkD4ytXGqNbFMso2t3puG";
    private final String BASE_URL = "https://www.searchapi.io/api/v1/search";
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(description = "搜索互联网获取最新信息，并返回精简后的搜索结果")
    public String searchWeb(
            @ToolParam(description = "搜索关键词") String query) {

        if (query == null || query.isBlank()) {
            return toJson(WebSearchResponseDTO.error("query 不能为空"));
        }

        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL)
                    .queryParam("engine", "baidu")
                    .queryParam("q", query)
                    .queryParam("api_key", API_KEY)
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();

            String responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            WebSearchResponseDTO dto = parseOrganicResults(responseBody);
            return toJson(dto);

        } catch (HttpClientErrorException e) {
            return toJson(WebSearchResponseDTO.error(
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString()));
        } catch (Exception e) {
            return toJson(WebSearchResponseDTO.error("搜索失败: " + e.getMessage()));
        }
    }
   private WebSearchResponseDTO parseOrganicResults(String rawJson) throws Exception {

        JsonNode root = objectMapper.readTree(rawJson);

        if (root.has("error")) {
            return WebSearchResponseDTO.error(root.get("error").asText());
        }

        JsonNode organicResults = root.get("organic_results");
        if (organicResults == null || !organicResults.isArray()) {
            return WebSearchResponseDTO.error("搜索结果为空");
        }

        List<WebSearchResultDTO> results = new ArrayList<>();

        for (JsonNode item : organicResults) {
            String title = item.path("title").asText("");
            String url = item.path("link").asText("");
            String snippet = item.path("snippet").asText("");

            // 过滤无效项
            if (title.isBlank() || url.isBlank()) {
                continue;
            }

            results.add(new WebSearchResultDTO(title, url, snippet));
        }

        // 限制条数，防止 token 爆炸（强烈建议）
        int MAX_RESULTS = 5;
        if (results.size() > MAX_RESULTS) {
            results = results.subList(0, MAX_RESULTS);
        }

        return WebSearchResponseDTO.ok(results);
    }
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON序列化失败\"}";
        }
    }
    private String handleHttpClientError(
            org.springframework.web.client.HttpClientErrorException e) {

        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();

        return switch (status) {
            case 400 -> buildError("请求参数错误(400): " + body);
            case 401 -> buildError("API Key 无效或未授权(401)");
            case 403 -> buildError("访问被拒绝(403)");
            case 429 -> buildError("请求过于频繁，请稍后再试(429)");
            default  -> buildError("HTTP 错误 " + status + ": " + body);
        };
    }
    private String buildError(String message) {
        ObjectNode errorNode = objectMapper.createObjectNode();
        errorNode.put("success", false);
        errorNode.put("error", message);
        return errorNode.toPrettyString();
    }
}