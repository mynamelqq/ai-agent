package com.ai.aigent.tools;

import com.ai.aigent.constant.FileConstant;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PDFGenerationTool {
    private String FILE_DIR= FileConstant.FILE_SAVE_DIR+"/pdf";
    @Tool(description = "生成 PDF 文件",returnDirect = false)
public String generatePDF(
            @ToolParam(description = "PDF 内容") String content,
            @ToolParam(description = "输出文件名") String fileName
    ) {

        String safeText = stripNonBmp(content);
        Path outputPath = Paths.get(FILE_DIR, fileName);

        try (PDDocument document = new PDDocument()) {

            // 使用中文字体（强烈推荐）
            InputStream fontStream =
            PDFGenerationTool.class
                .getClassLoader()

                .getResourceAsStream("fonts/NotoSerifCJKsc-Black.otf");

           PDType0Font font = PDType0Font.load(document, fontStream);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream cs =
                         new PDPageContentStream(document, page)) {

                cs.beginText();
                cs.setFont(font, 12);
                cs.setLeading(16);
                cs.newLineAtOffset(50, 770);

                for (String line : safeText.split("\n")) {
                    cs.showText(line);
                    cs.newLine();
                }

                cs.endText();
            }

            document.save(outputPath.toFile());
            return outputPath.toString();

        } catch (Exception e) {
            ToolDefinition tool = DefaultToolDefinition.builder()
        .name("generate_pdf")
        .description("生成 PDF 文件失败")
        .inputSchema("schema")
        .build();
            throw new ToolExecutionException(tool,e);
        }
    }
    public static String stripNonBmp(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                i++; // 跳过低位代理
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

}
