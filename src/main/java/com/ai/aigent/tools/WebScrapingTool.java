package com.ai.aigent.tools;

import com.ai.aigent.entity.dto.WebPageFetchResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WebScrapingTool {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Tool(description = "抓取指定网页的正文文本内容（不执行JS，仅用于信息提取）")
public String fetchWebPage(
        @ToolParam(description = "要抓取的网页URL") String url) {
        if (url == null || url.isBlank()) {
            return toJson(WebPageFetchResponseDTO.error(null, "url 不能为空"));
        }
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; SpringAI-WebFetcher/1.0)")
                    .timeout(10_000)
                    .followRedirects(true)
                    .get();

            // 1️⃣ 移除无关元素
            doc.select("script, style, noscript, iframe, header, footer, nav").remove();
            // 2️⃣ 抽取正文（优先 main / article）
            String text = extractMainContent(doc);
            // 3️⃣ 清洗文本
            text = normalizeText(text);
            // 4️⃣ 限制长度（防止 token 爆炸）
            int MAX_CHARS = 6000;
            if (text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS) + "...";
            }
            return toJson(WebPageFetchResponseDTO.ok(url, text));

        } catch (Exception e) {
            return toJson(WebPageFetchResponseDTO.error(url, e.getMessage()));
        }
    }
    private String extractMainContent(Document doc) {

        String[] selectors = {
                "article",
                "main",
                ".post-content",
                ".markdown-body",
                ".content",
                "body"
        };

        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element != null) {
                String text = element.text();
                if (text != null && text.length() > 300) { // 关键阈值
                    return text;
                }
            }
        }
        return "";
    }
    private String normalizeText(String text) {
        return text
                .replaceAll("\\s+", " ")
                .replaceAll("[\\u00A0]", " ")
                .trim();
    }
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON序列化失败\"}";
        }
    }
}
