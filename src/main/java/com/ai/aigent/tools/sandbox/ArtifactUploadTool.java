package com.ai.aigent.tools.sandbox;
import com.ai.aigent.sandbox.SandboxManager;
import com.ai.aigent.constant.FileConstant;
import com.qcloud.cos.COSClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import java.nio.file.*;
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
        sandbox.authorize(session, context.getContext().get("userId").toString());
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute() || relative.getNameCount() == 0) throw new IllegalArgumentException("需要相对路径");
        Path base = SandboxManager.directory(FileConstant.AGENT_RUN_SANDBOX_TEMP_DIR, session).toRealPath();
        Path file = base;
        for (Path part : relative) {
            if (part.toString().equals("..")) throw new IllegalArgumentException("非法路径");
            file = file.resolve(part);
            if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("不允许链接");
        }
        file = file.toRealPath();
        if (!file.startsWith(base) || !Files.isRegularFile(file) || Files.size(file) > 20L * 1024 * 1024)
            throw new IllegalArgumentException("产物不存在或超过大小上限");
        String key = "generate/" + session + "/" + UUID.randomUUID() + "/" + file.getFileName();
        cos.putObject(bucket, key, file.toFile());
        return Map.of("key", key, "url", cos.generatePresignedUrl(bucket, key,
            new Date(System.currentTimeMillis() + 9000000000000L)).toString());
    }
}
