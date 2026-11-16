package com.ai.aigent.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CosUploadService {

    @Value("${cos.bucket}")
    private String bucket;
    @Value("${cos.domain}")
    private String domain;
    @Value("${app.cos-enabled:true}")
    private boolean cosEnabled;
    @Autowired
    private COSClient cosClient;

    public String upload(MultipartFile file, String dir) throws IOException {
        requireCosEnabled();

        String ext = FilenameUtils.getExtension(file.getOriginalFilename());
        String key = dir + "/" + UUID.randomUUID() + "." + ext;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        PutObjectRequest request = new PutObjectRequest(
                bucket,
                key,
                file.getInputStream(),
                metadata
        );

        cosClient.putObject(request);

        return "https://" + bucket + ".cos." + cosClient.getClientConfig().getRegion().getRegionName()
                + ".myqcloud.com/" + key;
    }

    public Map<String, Object> createUploadSign(String suffix) {
        requireCosEnabled();

        // 1. 生成随机 COS Key
        String datePath = LocalDate.now().toString(); // 2026-02-05
        String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        String key = "ai-upload/" + datePath + "/" + fileName;

        // 2. 生成带过期时间的签名 URL（PUT 直传）
        Date expiration = new Date(System.currentTimeMillis() + 5 * 60 * 1000); // 5分钟

        GeneratePresignedUrlRequest req =
                new GeneratePresignedUrlRequest(bucket, key, HttpMethodName.PUT);
        req.setExpiration(expiration);

        URL uploadUrl = cosClient.generatePresignedUrl(req);

        // 3. 返回给前端
        Map<String, Object> resp = new HashMap<>();
        resp.put("uploadUrl", uploadUrl.toString());
        resp.put("key", key);
        resp.put("fileUrl", domain + "/" + key);
        resp.put("expireAt", expiration.getTime());

        return resp;
    }
    public Map<String, Object> createUploadAvatarSign(String suffix) {
        requireCosEnabled();

        // 1. 生成随机 COS Key
        String datePath = LocalDate.now().toString(); // 2026-02-05
        String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        String key = "ai-userAvatar/" + datePath + "/" + fileName;

        // 2. 生成带过期时间的签名 URL（PUT 直传）
        Date expiration = new Date(System.currentTimeMillis() + 5 * 60 * 1000); // 5分钟

        GeneratePresignedUrlRequest req =
                new GeneratePresignedUrlRequest(bucket, key, HttpMethodName.PUT);
        req.setExpiration(expiration);

        URL uploadUrl = cosClient.generatePresignedUrl(req);

        // 3. 返回给前端
        Map<String, Object> resp = new HashMap<>();
        resp.put("uploadUrl", uploadUrl.toString());
        resp.put("key", key);
        resp.put("fileUrl", domain + "/" + key);
        resp.put("expireAt", expiration.getTime());

        return resp;
    }

    private void requireCosEnabled() {
        if (!cosEnabled) {
            throw new IllegalStateException("COS uploads are disabled by app.cos-enabled=false");
        }
    }
}
