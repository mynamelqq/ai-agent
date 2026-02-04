package com.ai.aigent.tools;

import cn.hutool.core.io.FileUtil;
import com.ai.aigent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

public class FileOperationTool {

    public final String FILE_DIR = FileConstant.FILE_SAVE_DIR+"\\file"; // 建议配置化

    @Tool(description = "从指定文件中读取文本内容,文件都在绝对路径E:\\AAA project\\aigent\\aigent\\tmp\\file")
    public String readFile(
            @ToolParam(description = "要读取的文件名，例如 'test.txt'") String fileName) {
        String filePath = FILE_DIR + "\\" + fileName;
        try {
            return filePath+":"+FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "读取失败: " + e.getMessage();
        }
    }

    @Tool(description = "将内容写入指定文件，如果文件不存在则创建，存在则覆盖,文件将写入至E:\\AAA project\\aigent\\aigent\\tmp\\file")
    public String writeFile(
            @ToolParam(description = "要保存的文件名") String fileName,
            @ToolParam(description = "要写入文件的具体文本内容") String content) {
        String filePath = FILE_DIR + "\\"  + fileName;
        try {
            File dir = new File(FILE_DIR);
            if (!dir.exists()) dir.mkdirs();
            FileUtil.writeUtf8String(content, filePath);
            return "文件 '" + filePath + "' 写入成功。";
        } catch (Exception e) {
            return "写入失败: " + e.getMessage();
        }
    }
}
