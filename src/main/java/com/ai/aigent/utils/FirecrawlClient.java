package com.ai.aigent.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class FirecrawlClient {
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    @Autowired
    public FirecrawlClient(@Value("${firecrawl.api-key:${FIRECRAWL_API_KEY:}}") String apiKey,
                           @Value("${firecrawl.base-url:https://api.firecrawl.dev/v2}") String baseUrl) {
        this(apiKey, baseUrl, new RestTemplate());
    }

    public FirecrawlClient(String apiKey, String baseUrl, RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.restTemplate = restTemplate;
    }

    public JsonNode search(Map<String, Object> request) {
        return post("/search", request);
    }

    public JsonNode scrape(Map<String, Object> request) {
        return post("/scrape", request);
    }

    private JsonNode post(String path, Map<String, Object> request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 Firecrawl API Key（firecrawl.api-key 或 FIRECRAWL_API_KEY）");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        JsonNode response = restTemplate.postForObject(baseUrl + path,
                new HttpEntity<>(request, headers), JsonNode.class);
        if (response == null || !response.path("success").asBoolean(false)) {
            String error = response == null ? "空响应" : response.path("error").asText("请求失败");
            throw new IllegalStateException("Firecrawl 请求失败: " + error);
        }
        return response.path("data");
    }
}
