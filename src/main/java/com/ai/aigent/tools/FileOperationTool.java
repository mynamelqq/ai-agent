package com.ai.aigent.tools;

import com.ai.aigent.entity.dto.FileGenerateDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class FileOperationTool {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${cos.bucket}")
    private String bucket;
    @Value("${cos.region}")
    private String region;
    @Autowired
    private COSClient cosClient;
    @Tool(description = "将内容写入指定文本文件并上传到 COS，返回下载 URL（内容不超过 200KB）", returnDirect = false)
    public String writeFile(
            @ToolParam(description = "文件名（不含后缀）") String fileName,
            @ToolParam(description = "文件后缀，例如 txt、md、json") String fileType,
            @ToolParam(description = "要写入的文本内容") String textContent,
            ToolContext context
    ) {
        try {
            if (textContent == null || textContent.length() >= 1024 * 200) {
                return "文件内容超过 200KB，不允许调用";
            }
            if (fileType == null || fileType.isBlank()) {
                fileType = ".txt";
            } else if (!fileType.startsWith(".")) {
                fileType = "." + fileType;
            }

            String finalFileName = normalizeFileName(fileName, fileType);
            String chatId = context.getContext().get("chatId").toString();
            String key = "generate/" + chatId + "/" + finalFileName;

            byte[] bytes = textContent.getBytes(StandardCharsets.UTF_8);
            try (PipedOutputStream out = new PipedOutputStream();
                 PipedInputStream in = new PipedInputStream(out)) {

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try (OutputStream os = out) {
                        os.write(bytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(bytes.length);
                PutObjectRequest request = new PutObjectRequest(bucket, key, in, metadata);
                cosClient.putObject(request);
                future.join();
            }

            String url = String.format("https://%s.cos.%s.myqcloud.com/%s",
                    bucket,
                    cosClient.getClientConfig().getRegion().getRegionName(),
                    key);
            return toJson(FileGenerateDTO.ok(url, bytes.length, finalFileName));
        } catch (Exception e) {
            return toJson(FileGenerateDTO.error(e.getMessage()));
        }
    }

    @Tool(description = "生成 PDF 文件并上传到 COS，返回下载 URL", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "PDF 内容") String content,
            @ToolParam(description = "文件名（不含后缀）") String fileName,
            ToolContext context
    ) {
        String safeText = stripNonBmp(content == null ? "" : content);
        String finalFileName = normalizeFileName(fileName, ".pdf");
        String chatId = context.getContext().get("chatId").toString();
        String key = "generate/" + chatId + "/" + finalFileName;

        try (PipedOutputStream out = new PipedOutputStream();
             PipedInputStream in = new PipedInputStream(out)) {

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try (PDDocument document = new PDDocument()) {
                    InputStream fontStream = SpecialFileGenerationTool.class
                            .getClassLoader()
                            .getResourceAsStream("fonts/NotoSerifCJKsc-Black.otf");
                    PDType0Font font = PDType0Font.load(document, fontStream);
                    PDPage page = new PDPage(PDRectangle.A4);
                    document.addPage(page);

                    try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                        cs.beginText();
                        cs.setFont(font, 12);
                        cs.setLeading(16);
                        cs.newLineAtOffset(50, 770);
                        for (String line : safeText.split("\\n")) {
                            cs.showText(line);
                            cs.newLine();
                        }
                        cs.endText();
                    }
                    document.save(out);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("application/pdf");
            PutObjectRequest request = new PutObjectRequest(bucket, key, in, metadata);
            cosClient.putObject(request);
            future.join();

            String url = String.format("https://%s.cos.%s.myqcloud.com/%s",
                    bucket,
                    cosClient.getClientConfig().getRegion().getRegionName(),
                    key);
            return toJson(FileGenerateDTO.ok(url, 0, finalFileName));
        } catch (Exception e) {
            return toJson(FileGenerateDTO.error("PDF generation failed: " + e.getMessage()));
        }
    }

    @Tool(description = "生成 Excel 文件并上传到 COS，返回下载 URL")
    public String generateExcel(
            @ToolParam(description = "Excel 二维数组 JSON 字符串，例如 [[\"姓名\",\"年龄\"],[\"张三\",\"18\"]]") String rowsJson,
            @ToolParam(description = "文件名（不含后缀）") String fileName,
            ToolContext context
    ) {
        List<List<String>> rows;
        try {
            rows = objectMapper.readValue(rowsJson, new TypeReference<List<List<String>>>() {
            });
        } catch (Exception e) {
            return toJson(FileGenerateDTO.error("rows JSON parse failed"));
        }

        if (rows == null || rows.isEmpty()) {
            return toJson(FileGenerateDTO.error("Excel generation failed: rows cannot be empty"));
        }

        int headerSize = rows.get(0).size();
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).size() != headerSize) {
                return toJson(FileGenerateDTO.error(String.format(
                        "Excel generation failed: row %d column count mismatch, expected %d but got %d",
                        i + 1, headerSize, rows.get(i).size())));
            }
        }

        String finalFileName = normalizeFileName(fileName, ".xlsx");
        String chatId = context.getContext().get("chatId").toString();
        String key = "generate/" + chatId + "/" + finalFileName;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Sheet1");
                CellStyle headerStyle = wb.createCellStyle();
                Font headerFont = wb.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                for (int i = 0; i < rows.size(); i++) {
                    Row row = sheet.createRow(i);
                    List<String> cols = rows.get(i);
                    for (int j = 0; j < cols.size(); j++) {
                        Cell cell = row.createCell(j);
                        String value = cols.get(j);
                        if (isNumeric(value)) {
                            cell.setCellValue(Double.parseDouble(value));
                            cell.setCellType(CellType.NUMERIC);
                        } else {
                            cell.setCellValue(value);
                        }
                        if (i == 0) {
                            cell.setCellStyle(headerStyle);
                        }
                    }
                }

                for (int i = 0; i < headerSize; i++) {
                    sheet.autoSizeColumn(i);
                    int currentWidth = sheet.getColumnWidth(i);
                    sheet.setColumnWidth(i, Math.min(currentWidth + 512, 255 * 256));
                }
                wb.write(baos);
            }

            byte[] excelBytes = baos.toByteArray();
            try (ByteArrayInputStream bais = new ByteArrayInputStream(excelBytes)) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                metadata.setContentLength(excelBytes.length);
                PutObjectRequest request = new PutObjectRequest(bucket, key, bais, metadata);
                cosClient.putObject(request);
            }

            String url = String.format("https://%s.cos.%s.myqcloud.com/%s", bucket, region, key);
            return toJson(FileGenerateDTO.ok(url, excelBytes.length, finalFileName));
        } catch (Exception e) {
            log.error(e.getMessage());
            return toJson(FileGenerateDTO.error("Excel generation failed: " + e.getMessage()));
        } finally {
            try {
                baos.close();
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
    }

    @Tool(description = "生成 Word(docx) 文件并上传到 COS，返回下载 URL")
    public String generateDocx(
            @ToolParam(description = "Word 文本内容，换行将写成新段落") String content,
            @ToolParam(description = "文件名（不含后缀）") String fileName,
            ToolContext context
    ) {
        if (content == null || content.isEmpty()) {
            return toJson(FileGenerateDTO.error("Word generation failed: content cannot be empty"));
        }

        String finalFileName = normalizeFileName(fileName, ".docx");
        String chatId = context.getContext().get("chatId").toString();
        String key = "generate/" + chatId + "/" + finalFileName;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            try (XWPFDocument document = new XWPFDocument()) {
                String[] lines = content.split("\\R", -1);
                for (String line : lines) {
                    XWPFParagraph paragraph = document.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(line);
                }
                document.write(baos);
            }

            byte[] docBytes = baos.toByteArray();
            try (ByteArrayInputStream bais = new ByteArrayInputStream(docBytes)) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                metadata.setContentLength(docBytes.length);
                PutObjectRequest request = new PutObjectRequest(bucket, key, bais, metadata);
                cosClient.putObject(request);
            }

            String url = String.format("https://%s.cos.%s.myqcloud.com/%s", bucket, region, key);
            return toJson(FileGenerateDTO.ok(url, docBytes.length, finalFileName));
        } catch (Exception e) {
            log.error(e.getMessage());
            return toJson(FileGenerateDTO.error("Word generation failed: " + e.getMessage()));
        } finally {
            try {
                baos.close();
            } catch (IOException e) {
                log.error(e.getMessage());
            }
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
                i++;
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON serialize failed", e);
            return "{\"success\":false,\"error\":\"JSON serialize failed\"}";
        }
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String normalizeFileName(String fileName, String extension) {
        String extLower = extension.toLowerCase(Locale.ROOT);
        if (fileName == null || fileName.isBlank()) {
            return UUID.randomUUID() + extension;
        }
        if (fileName.toLowerCase(Locale.ROOT).endsWith(extLower)) {
            return fileName;
        }
        return fileName + extension;
    }
}