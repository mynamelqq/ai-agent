package com.ai.aigent.tools;

import com.ai.aigent.constant.FileConstant;
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
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
@Slf4j
@Component
public class FileOperationTool {
    private final ObjectMapper objectMapper = new ObjectMapper();
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
    @Value("${cos.region}")
    private String region;
    @Autowired
    private COSClient cosClient;
//    @Tool(description = "将内容写入指定文本文件，并返回下载URL,如果文件长度超过100KB则不调用工具.",returnDirect = false)
//    public String writeFile(
//            @ToolParam(description = "文件名") String fileName,
//            @ToolParam(description = "文件后缀,例:txt,md,json") String fileType,
//            @ToolParam(description = "要写入的内容") String textContent) {
//        try {
//            if(textContent.length()>=1024*200){//100KB
//                return "文件长度超过200KB，不许调用";
//            }
//            if (!fileType.startsWith(".")) fileType = "." + fileType;
//            PipedOutputStream out = new PipedOutputStream();
//            PipedInputStream in = new PipedInputStream(out);
//            // 异步写入文本内容
//            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                try (OutputStream os = out) {
//                    os.write(textContent.getBytes(StandardCharsets.UTF_8));
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//            });
//            String key = "generate/" + UUID.randomUUID() + fileType;
//            ObjectMetadata metadata = new ObjectMetadata();
//            metadata.setContentLength(textContent.getBytes(StandardCharsets.UTF_8).length);
//            PutObjectRequest request = new PutObjectRequest(bucket, key, in, metadata);
//            cosClient.putObject(request);
//            future.join(); // 等待写入完成
//            return String.format("https://%s.cos.%s.myqcloud.com/%s",
//                    bucket,
//                    cosClient.getClientConfig().getRegion().getRegionName(),
//                    key);
//        } catch (Exception e) {
//            return "文件生成失败";
//        }
//    }
      @Tool(description = "生成 PDF 文件并上传到 COS，返回下载URL", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Excel内容，纯文本") String content,
            @ToolParam(description = "输出文件名(不含后缀)") String fileName
    ) {
        String safeText = stripNonBmp(content);

        // COS key
        String key = "generate/" + UUID.randomUUID() + ".pdf";

        try (
                PipedOutputStream out = new PipedOutputStream();
                PipedInputStream in = new PipedInputStream(out)
        ) {
            // 异步生成 PDF 写入流
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
                        for (String line : safeText.split("\n")) {
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
            // 等待 PDF 写入完成
            future.join();
            // 返回 COS URL
            return toJson(FileGenerateDTO.ok(String.format("https://%s.cos.%s.myqcloud.com/%s",
                    bucket,
                    cosClient.getClientConfig().getRegion().getRegionName(),
                    key),0));
        } catch (Exception e) {
            return toJson(FileGenerateDTO.error("PDF生成失败"+e.getMessage()));
        }
    }
    public static String stripNonBmp(String text)
    { if (text == null) { return null; }

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch))
            { i++;
                // 跳过低位代理
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
            log.error("JSON序列化失败", e);
            return "{\"success\":false,\"error\":\"JSON序列化失败\"}";
        }
    }
    @Tool(description = "生成 Excel 文件并上传到 COS，返回下载URL")
    public String generateExcel(
            @ToolParam(description = "Excel二维数组JSON字符串，例如 [[\"姓名\",\"年龄\"],[\"张三\",\"18\"]]")
            String rowsJson,
            @ToolParam(description = "文件名(不含后缀)")
            String fileName
    ){
        List<List<String>> rows;
        try {
            rows = new ObjectMapper().readValue(
                    rowsJson,
                    new TypeReference<List<List<String>>>() {}
            );
        } catch (Exception e) {
            return toJson(FileGenerateDTO.error("rows JSON解析失败"));
        }
        // 参数验证
        if (rows == null || rows.isEmpty()) {
            return toJson(FileGenerateDTO.error("Excel生成失败：数据不能为空"));
        }
        // 验证数据格式
        int headerSize = rows.get(0).size();
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).size() != headerSize) {
                return toJson(FileGenerateDTO.error(
                    String.format("Excel生成失败：第%d行列数不一致，期望%d列，实际%d列",
                        i + 1, headerSize, rows.get(i).size())
                ));
            }
        }
        // 处理文件名
        if (!fileName.endsWith(".xlsx")) {
            fileName += ".xlsx";
        }
        String key = "generate/" + UUID.randomUUID() + ".xlsx";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // 1. 生成 Excel 到内存
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Sheet1");
                // 创建标题行样式
                CellStyle headerStyle = wb.createCellStyle();
                Font headerFont = wb.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                // 创建数据行
                for (int i = 0; i < rows.size(); i++) {
                    Row row = sheet.createRow(i);
                    List<String> cols = rows.get(i);

                    for (int j = 0; j < cols.size(); j++) {
                        Cell cell = row.createCell(j);
                        String value = cols.get(j);

                        // 根据内容类型设置值
                        if (isNumeric(value)) {
                            cell.setCellValue(Double.parseDouble(value));
                            cell.setCellType(CellType.NUMERIC);
                        } else {
                            cell.setCellValue(value);
                        }
                        // 为标题行应用样式
                        if (i == 0) {
                            cell.setCellStyle(headerStyle);
                        }
                    }
                }
                // 在所有数据写入后调整列宽
                for (int i = 0; i < headerSize; i++) {
                    sheet.autoSizeColumn(i);
                    int currentWidth = sheet.getColumnWidth(i);
                    sheet.setColumnWidth(i, Math.min(currentWidth + 512, 255 * 256));
                }
                wb.write(baos);
            }
            // 2. 上传到 COS
            byte[] excelBytes = baos.toByteArray();
            try (ByteArrayInputStream bais = new ByteArrayInputStream(excelBytes)) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                metadata.setContentLength(excelBytes.length);
//                metadata.setContentDisposition("attachment; filename=\"" + fileName + "\"");
                PutObjectRequest request = new PutObjectRequest(bucket, key, bais,metadata);
                cosClient.putObject(request);
            }
            // 3. 返回 COS URL
            String url = String.format("https://%s.cos.%s.myqcloud.com/%s",
                    bucket,
                    region,
                    key);

            return toJson(FileGenerateDTO.ok(url,excelBytes.length));
        } catch (Exception e) {
            log.error(e.getMessage());
            return toJson(FileGenerateDTO.error("Excel生成失败: " + e.getMessage()));
        } finally {
            try {
                baos.close();
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
    }

    /**
     * 判断字符串是否为数值
     */
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

}
