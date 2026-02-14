package com.ai.aigent.utils;

import com.ai.aigent.entity.VO.BochaWebSearchResponse;
import com.ai.aigent.entity.dto.BochaWebSearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;



@Service
public class BochaWebSearchClient {

    @Value("${bocha.api-key}")
    private String apiKey;

    @Value("${bocha.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public BochaWebSearchResponse search(BochaWebSearchRequest req) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<BochaWebSearchRequest> entity =
                new HttpEntity<>(req, headers);

        ResponseEntity<BochaWebSearchResponse> response = restTemplate.exchange(
                baseUrl + "/web-search",
                HttpMethod.POST,
                entity,
                BochaWebSearchResponse.class
        );

        return response.getBody();
    }
}