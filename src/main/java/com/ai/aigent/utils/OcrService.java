package com.ai.aigent.utils;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
@Service
public class OcrService {
    private static final String API_URL =
            "https://api.jiekou.ai/openai/v1/chat/completions";
   private static String API_KEY;

    @Value("${spring.ai.openai.api-key}")
    private String apiKeyInstance;
    @PostConstruct
    public void init() {
        API_KEY = apiKeyInstance;
    }
    public static String getApiKey() {
        return API_KEY;
    }
    public static String ocr(MultipartFile file) throws Exception {
        // 1. 将文件转换为 base64
        String base64 = Base64.getEncoder().encodeToString(file.getBytes());
        String mime = file.getContentType();
        String dataUrl = "data:" + mime + ";base64," + base64;
        // 2. 构造请求 JSON
        Map<String, Object> body = new HashMap<>();
        body.put("model", "deepseek/deepseek-ocr-2");

        // 创建 content 数组
        List<Map<String, Object>> contentList = new ArrayList<>();

        // 添加文本部分
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "提取图片中的所有文字");
        contentList.add(textPart);

        // 添加图片部分 - 注意：DeepSeek OCR 使用 image_url 格式
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");

        // 创建 image_url 对象
        Map<String, String> imageUrl = new HashMap<>();
        imageUrl.put("url", dataUrl);
        imagePart.put("image_url", imageUrl);
        contentList.add(imagePart);

        // 创建消息
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", contentList);

        body.put("messages", Collections.singletonList(message));
        body.put("max_tokens", 4096);

        // 3. 发送请求
        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getApiKey());

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        try {
            Map<String, Object> resp = rest.postForObject(API_URL, req, Map.class);

            // 4. 解析返回
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> msg = (Map<String, Object>) choice.get("message");
                return (String) msg.get("content");
            }
            return "未识别到文字";

        } catch (HttpClientErrorException e) {
            throw e;
        }
    }
}
