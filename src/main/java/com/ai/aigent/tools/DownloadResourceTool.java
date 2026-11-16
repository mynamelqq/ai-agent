package com.ai.aigent.tools;

import com.ai.aigent.entity.dto.DownloadResourceResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Component
public class DownloadResourceTool {
    @Value("${cos.bucket}")
    private String bucket;
    @Autowired
    private COSClient cosClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Tool(description = "下载指定 URL 的资源并上传到 COS，返回下载链接")
public String downloadResource(
        @ToolParam(description = "资源 URL（仅支持 http/https）") String url,
        @ToolParam(description = "保存的文件名，如 example.zip") String fileName) {

    if (url == null || url.isBlank()) {
        return toJson(DownloadResourceResponseDTO.error(null, "url 不能为空"));
    }
    if (fileName == null || fileName.isBlank()) {
        return toJson(DownloadResourceResponseDTO.error(url, "fileName 不能为空"));
    }
    if (!isHttpUrl(url)) {
        return toJson(DownloadResourceResponseDTO.error(url, "仅支持 http/https 协议"));
    }
    // 1️⃣ 清理文件名（防路径穿越）
    fileName = sanitizeFileName(fileName);

    if (cosClient == null || bucket == null || bucket.isBlank()) {
        return toJson(DownloadResourceResponseDTO.error(url, "COS 未初始化"));
    }

    HttpURLConnection conn = null;
    try {
        // 2️⃣ 建立连接
        URI uri = URI.create(url);
        conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setInstanceFollowRedirects(true);
        fillBrowserHeaders(conn, url);
        int status = conn.getResponseCode();
        if (status >= 400) {
            return toJson(DownloadResourceResponseDTO.error(
                    url, "HTTP 错误: " + status));
        }
        String contentType = conn.getContentType();

        // ⚠️ 明确拒绝 HTML
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            return toJson(DownloadResourceResponseDTO.error(
                    url, "返回的是 HTML 页面，疑似 404 或反爬"));
        }
        long MAX_SIZE = 10 * 1024 * 1024; // 10MB
        long contentLength = conn.getContentLengthLong();
        if (contentLength > 0 && contentLength > MAX_SIZE) {
            return toJson(DownloadResourceResponseDTO.error(
                    url, "文件超过大小限制(10MB)"));
        }

        // 3️⃣ 下载到内存（带大小限制）
        byte[] fileBytes;
        long size;
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            size = copyWithLimit(in, out, MAX_SIZE);
            fileBytes = out.toByteArray();
        }

        // 4️⃣ 上传 COS
        String key = buildCosKey(fileName);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(fileBytes.length);
        if (contentType != null && !contentType.isBlank()) {
            metadata.setContentType(contentType);
        } else {
            metadata.setContentType("application/octet-stream");
        }

        try (ByteArrayInputStream uploadIn = new ByteArrayInputStream(fileBytes)) {
            PutObjectRequest request = new PutObjectRequest(bucket, key, uploadIn, metadata);
            cosClient.putObject(request);
        }
        String cosUrl = String.format("https://%s.cos.%s.myqcloud.com/%s",
                bucket,
                cosClient.getClientConfig().getRegion().getRegionName(),
                key);

        return toJson(DownloadResourceResponseDTO.ok(
                url, fileName, key, cosUrl, size));

    } catch (Exception e) {
        return toJson(DownloadResourceResponseDTO.error(url, e.getMessage()));
    } finally {
        if (conn != null) {
            conn.disconnect();
        }
    }
}
    private boolean isHttpUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }
    private String buildCosKey(String fileName) {
        String date = LocalDate.now().toString();
        String unique = UUID.randomUUID().toString().replace("-", "");
        String safeName = Paths.get(fileName).getFileName().toString();
        return "download/" + date + "/" + unique + "_" + safeName;
    }
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON序列化失败\"}";
        }
    }
    private String sanitizeFileName(String fileName) {
        // 只保留最后一级文件名
        fileName = Paths.get(fileName).getFileName().toString();

        // 去除奇怪字符
        fileName = fileName.replaceAll("[\\\\/\\:\\*\\?\"<>\\|]", "_");

        return fileName;
    }
    private void fillBrowserHeaders(HttpURLConnection conn, String referer) {
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/121.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        conn.setRequestProperty("Connection", "keep-alive");

        if (referer != null) {
            conn.setRequestProperty("Referer", referer);
        }
    }
    private long copyWithLimit(
            InputStream in, ByteArrayOutputStream out, long maxBytes) throws IOException {

        byte[] buffer = new byte[8192];
        long total = 0;
        int read;

        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("下载内容超过限制");
            }
            out.write(buffer, 0, read);
        }
        return total;
    }
}
