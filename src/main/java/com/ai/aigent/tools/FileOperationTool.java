package com.ai.aigent.tools;

import cn.hutool.core.io.FileUtil;
import com.ai.aigent.constant.FileConstant;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import org.apache.commons.io.FilenameUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class FileOperationTool {

    public final String FILE_DIR = FileConstant.FILE_SAVE_DIR+"\\file"; // 建议配置化

//    @Tool(description = "从指定文件中读取文本内容,文件都在绝对路径E:\\AAA project\\aigent\\aigent\\tmp\\file")
//    public String readFile(
//            @ToolParam(description = "要读取的文件名，例如 'test.txt'") String fileName) {
//        String filePath = FILE_DIR + "\\" + fileName;
//        try {
//            return filePath+":"+FileUtil.readUtf8String(filePath);
//        } catch (Exception e) {
//            return "读取失败: " + e.getMessage();
//        }
//    }
     @Value("${cos.bucket}")
    private String bucket;
    @Autowired
    private COSClient cosClient;
    @Tool(description = "将内容写入指定文本文件，并返回下载URL,如果文件长度超过100KB则不调用工具.")
    public String writeFile(
            @ToolParam(description = "文件名") String fileName,
            @ToolParam(description = "文件后缀,例:txt,md,json") String fileType,
            @ToolParam(description = "要写入的内容") String textContent) {
        try {
            if(textContent.length()>=1024*200){//100KB
                return "文件长度超过200KB，不许调用";
            }
            if (!fileType.startsWith(".")) fileType = "." + fileType;
            PipedOutputStream out = new PipedOutputStream();
            PipedInputStream in = new PipedInputStream(out);
            // 异步写入文本内容
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try (OutputStream os = out) {
                    os.write(textContent.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            String key = "generate/" + UUID.randomUUID() + fileType;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(textContent.getBytes(StandardCharsets.UTF_8).length);
            PutObjectRequest request = new PutObjectRequest(bucket, key, in, metadata);
            cosClient.putObject(request);
            future.join(); // 等待写入完成
            return String.format("https://%s.cos.%s.myqcloud.com/%s",
                    bucket,
                    cosClient.getClientConfig().getRegion().getRegionName(),
                    key);

        } catch (Exception e) {
            return "文件生成失败";
        }
    }

}
