package com.ai.aigent.service;

import com.ai.aigent.entity.dto.VideoUnderstandReq;
import com.ai.aigent.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class VideoUnderstandingService {

    private static final String DEFAULT_MODEL = "glm-4.6v";
    private static final String DEFAULT_PROMPT = "请完整理解这个视频，输出简洁摘要，并列出关键事件和时间顺序。";
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final double DEFAULT_TEMPERATURE = 0.3;

    @Value("${spring.ai.zhipuai.api-key}")
    private String apiKey;
    @Value("${zhipu.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String understand(VideoUnderstandReq req) {
        if (req == null || isBlank(req.getVideoUrl())) {
            throw BusinessException.badRequest("videoUrl 不能为空");
        }
        if (!req.getVideoUrl().startsWith("http://") && !req.getVideoUrl().startsWith("https://")) {
            throw BusinessException.badRequest("videoUrl 必须是 http/https 地址");
        }
        if (isBlank(apiKey)) {
            throw BusinessException.internalError("未配置 spring.ai.zhipuai.api-key");
        }

        String model = isBlank(req.getModel()) ? DEFAULT_MODEL : req.getModel();
        String prompt = isBlank(req.getPrompt()) ? DEFAULT_PROMPT : req.getPrompt();
        int maxTokens = req.getMaxTokens() == null ? DEFAULT_MAX_TOKENS : req.getMaxTokens();
        double temperature = req.getTemperature() == null ? DEFAULT_TEMPERATURE : req.getTemperature();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = buildRequestBody(req.getVideoUrl(), model, prompt, maxTokens, temperature);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            return extractAssistantText(Objects.requireNonNull(response.getBody(), "响应体为空"));
        } catch (HttpStatusCodeException e) {
            log.error("视频理解请求失败: {}", e.getResponseBodyAsString());
            throw BusinessException.badRequest("视频理解请求失败: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw BusinessException.internalError("视频理解服务异常: " + e.getMessage());
        }
    }

   private Map<String, Object> buildRequestBody(
            String videoUrl,
            String model,
            String prompt,
            int maxTokens,
            double temperature) {

        Map<String, Object> requestBody = new HashMap<>();

        requestBody.put("model", model);
        requestBody.put("stream", false);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);

        // video
        Map<String, Object> videoUrlObj = new HashMap<>();
        videoUrlObj.put("url", videoUrl);

        Map<String, Object> videoPart = new HashMap<>();
        videoPart.put("type", "video_url");
        videoPart.put("video_url", videoUrlObj);

        // text
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);

        // content
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(videoPart);
        content.add(textPart);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);

        requestBody.put("messages", List.of(userMessage));

        // thinking（可选但推荐）
        Map<String, Object> thinking = new HashMap<>();
        thinking.put("type", "disabled");
        requestBody.put("thinking", thinking);

        return requestBody;
    }

    private String extractAssistantText(Map<String, Object> responseBody) {
        Object choicesObj = responseBody.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw BusinessException.internalError("模型返回异常: choices 为空");
        }

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw BusinessException.internalError("模型返回异常: choice 格式错误");
        }
        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw BusinessException.internalError("模型返回异常: message 缺失");
        }
        Object contentObj = messageMap.get("content");
        String text = flattenContent(contentObj);
        if (isBlank(text)) {
            throw BusinessException.internalError("模型未返回可读文本");
        }
        return text.trim();
    }

    private String flattenContent(Object contentObj) {
        if (contentObj == null) {
            return "";
        }
        if (contentObj instanceof String s) {
            return s;
        }
        if (contentObj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    if (text != null) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(text);
                    }
                } else if (item != null) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(item);
                }
            }
            return sb.toString();
        }
        return String.valueOf(contentObj);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
