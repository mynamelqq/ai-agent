package com.ai.aigent.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class CosUploadService {

    @Value("${cos.bucket}")
    private String bucket;

    @Autowired
    private COSClient cosClient;

    public String upload(MultipartFile file, String dir) throws IOException {

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
}
