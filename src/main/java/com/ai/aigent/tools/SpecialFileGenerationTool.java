package com.ai.aigent.tools;

public class SpecialFileGenerationTool {
//    private String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/pdf";
//    @Value("${cos.bucket}")
//    private String bucket;
//
//    @Autowired
//    private COSClient cosClient;
//
//    @Tool(description = "生成 PDF 文件并上传到 COS，返回下载URL", returnDirect = false)
//    public String generatePDF(
//            @ToolParam(description = "PDF内容") String content,
//            @ToolParam(description = "输出文件名(不含后缀)") String fileName
//    ) {
//        String safeText = stripNonBmp(content);
//
//        // COS key
//        String key = "generate/" + UUID.randomUUID() + ".pdf";
//
//        try (
//                PipedOutputStream out = new PipedOutputStream();
//                PipedInputStream in = new PipedInputStream(out)
//        ) {
//            // 异步生成 PDF 写入流
//            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                try (PDDocument document = new PDDocument()) {
//                    InputStream fontStream = SpecialFileGenerationTool.class
//                            .getClassLoader()
//                            .getResourceAsStream("fonts/NotoSerifCJKsc-Black.otf");
//                    PDType0Font font = PDType0Font.load(document, fontStream);
//                    PDPage page = new PDPage(PDRectangle.A4);
//                    document.addPage(page);
//                    try (PDPageContentStream cs =
//                                 new PDPageContentStream(document, page)) {
//
//                        cs.beginText();
//                        cs.setFont(font, 12);
//                        cs.setLeading(16);
//                        cs.newLineAtOffset(50, 770);
//
//                        for (String line : safeText.split("\n")) {
//                            cs.showText(line);
//                            cs.newLine();
//                        }
//
//                        cs.endText();
//                    }
//                    document.save(out);
//
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            });
//            ObjectMetadata metadata = new ObjectMetadata();
//            metadata.setContentType("application/pdf");
//            PutObjectRequest request = new PutObjectRequest(bucket, key, in, metadata);
//            cosClient.putObject(request);
//            // 等待 PDF 写入完成
//            future.join();
//            // 返回 COS URL
//            return String.format("https://%s.cos.%s.myqcloud.com/%s",
//                    bucket,
//                    cosClient.getClientConfig().getRegion().getRegionName(),
//                    key);
//        } catch (Exception e) {
//            throw new RuntimeException("PDF生成并上传失败", e);
//        }
//    }
//    public static String stripNonBmp(String text)
//    { if (text == null) { return null; }
//
//        StringBuilder sb = new StringBuilder(text.length());
//        for (int i = 0; i < text.length(); i++) {
//            char ch = text.charAt(i);
//            if (Character.isHighSurrogate(ch))
//            { i++;
//                // 跳过低位代理
//                continue;
//            }
//            sb.append(ch);
//        }
//        return sb.toString();
//    }
//
//    @Tool(description = "生成 Excel 文件并上传到 COS，返回下载URL", returnDirect = false)
//    public String generateExcel(
//            @ToolParam(description = "表格数据，二维数组，第一行是表头")
//            List<List<String>> rows,
//            @ToolParam(description = "文件名(不含后缀)")
//            String fileName
//    ) {
//
//        if (rows == null || rows.isEmpty()) {
//            throw new RuntimeException("Excel数据不能为空");
//        }
//
//        if (!fileName.endsWith(".xlsx")) {
//            fileName += ".xlsx";
//        }
//
//        String key = "generate/" + UUID.randomUUID() + ".xlsx";
//
//        try (
//                PipedOutputStream out = new PipedOutputStream();
//                PipedInputStream in = new PipedInputStream(out)
//        ) {
//
//            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                try (Workbook wb = new XSSFWorkbook()) {
//
//                    Sheet sheet = wb.createSheet("Sheet1");
//
//                    for (int i = 0; i < rows.size(); i++) {
//                        Row row = sheet.createRow(i);
//                        List<String> cols = rows.get(i);
//
//                        for (int j = 0; j < cols.size(); j++) {
//                            Cell cell = row.createCell(j);
//                            cell.setCellValue(cols.get(j));
//                        }
//                    }
//                    for (int i = 0; i < rows.get(0).size(); i++) {
//                        sheet.autoSizeColumn(i);
//                    }
//                    wb.write(out);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            });
//            ObjectMetadata metadata = new ObjectMetadata();
//            metadata.setContentType(
//                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
//            );
//            PutObjectRequest request = new PutObjectRequest(bucket, key, in, metadata);
//            cosClient.putObject(request);
//            future.join();
//            return String.format("https://%s.cos.%s.myqcloud.com/%s",
//                    bucket,
//                    cosClient.getClientConfig().getRegion().getRegionName(),
//                    key);
//        } catch (Exception e) {
//            throw new RuntimeException("Excel生成失败", e);
//        }
//    }

}
//            ToolDefinition tool = DefaultToolDefinition.builder()
//            .name("generate_pdf")
//            .description("生成 PDF 文件失败")
//            .inputSchema("schema")
//            .build();
//            throw new ToolExecutionException(tool,e);
