package com.ai.aigent.entity;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tencent.cos")
public class CosProperties {
    /**
     * 访问密钥 ID
     */
    private String secretId;

    /**
     * 访问密钥 Key
     */
    private String secretKey;

    /**
     * 区域
     */
    private String region;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 存储桶所属用户的 APPID
     */
    private String bucketAppId;

    /**
     * 自定义域名
     */
    private String domain;

    /**
     * 上传配置
     */
    private Upload upload = new Upload();

    /**
     * 预签名URL配置
     */
    private Presign presign = new Presign();



    @Data
    public static class Upload {
        private Long maxSize = 10485760L; // 10MB
        private String allowExtensions = ".jpg,.jpeg,.png,.gif,.pdf,.doc,.docx,.xls,.xlsx";
    }

    @Data
    public static class Presign {
        private Long expiry = 3600L; // 1小时
        private Long downloadExpiry = 1800L; // 30分钟
        private Long uploadExpiry = 7200L; // 2小时
    }
}