package com.ai.aigent.tools.sandbox;
import com.ai.aigent.sandbox.SandboxManager;
import com.qcloud.cos.COSClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import java.util.*;
@Component
public class ArtifactUploadTool {
    private final SandboxManager sandbox;
    private final COSClient cos;
    @Value("${cos.bucket}") private String bucket;
    public ArtifactUploadTool(SandboxManager sandbox, COSClient cos) { this.sandbox = sandbox; this.cos = cos; }
    @Tool(description = "上传当前会话 /workspace 下的产物到 COS，传入相对路径，最大20MB；返回有效期150分钟下载链接")
    public Map<String, String> publishArtifact(String relativePath, ToolContext context) throws Exception {
        String session = context.getContext().get("chatId").toString();
        String token = Objects.requireNonNull(context.getContext().get("sandboxRunToken"), "缺少沙箱运行令牌").toString();
        try (var snapshot = sandbox.snapshot(session, context.getContext().get("userId").toString(), token, relativePath)) {
            String key = "generate/" + session + "/" + UUID.randomUUID() + "/" + snapshot.name();
            cos.putObject(bucket, key, snapshot.file().toFile());
            return Map.of("key", key, "url", cos.generatePresignedUrl(bucket, key,
                new Date(System.currentTimeMillis() + 9000000000000L)).toString());
        }
    }
}
