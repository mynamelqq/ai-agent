package com.ai.aigent.utils;

import com.ai.aigent.constant.AiConstants;
import com.ai.aigent.demo.image.VisionPerception;
import com.ai.aigent.entity.domin.AiAttachment;
import com.ai.aigent.service.AiAttachmentService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import jakarta.annotation.Resource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.tika.Tika;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttachmentReaderUtil {
    @Autowired
    private COSClient cosClient;
    public static final long SMALL_FILE_LIMIT = 80 * 1024; // 50KB
    @Resource
    @Qualifier("attachmentVectorStore")
    private VectorStore attachmentVectorStore;
    @Autowired
    private VisionPerception visionPerception;
    @Autowired
    private AiAttachmentService attachmentService;
  public static List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start = end - overlap;
            if (start < 0) start = 0;
        }
        return chunks;
    }
    public COSObject getObject(String bucketName, String url) {
        String key = url.substring(url.indexOf(AiConstants.suffix_url) + AiConstants.suffix_url.length());
        return cosClient.getObject(bucketName, key);
    }
    public String readContent(AiAttachment attachment) {
        String url = attachment.getFileUrl();
        String key = url.substring(url.indexOf(AiConstants.suffix_url) + AiConstants.suffix_url.length());
        String fileType = attachment.getFileType();
        Long fileSize = attachment.getFileSize();
//        if (fileType.startsWith("image/")) {
//            COSObject object = getObject(AiConstants.bulk, key);
//            try {
//                return "User uploaded file: "+attachment.getFileName()+". Here are its contents:\n"+visionPerception.getVisionDescription(url);
//            } catch (Exception e) {
//                return e.getMessage();
//            }
//        }
//        else if(fileType.startsWith("audio/")){
//
//        }

        // ✅ 小文件：直接读取全文
        if (fileSize < SMALL_FILE_LIMIT) {
            COSObject object = cosClient.getObject("efrewew-1402637531", key);

            try (InputStream is = object.getObjectContent()) {
//                long contentLength = object.getObjectMetadata().getContentLength();
                return "User uploaded file: "+attachment.getFileName()+". Here are its contents:\n"+readContentByContentType(is, fileType);
            } catch (Exception e) {
                throw new RuntimeException("读取 COS 文件失败: " + key, e);
            }
        }
        attachmentService.asyncEmbedding(attachment);

//        // ✅ 大文件：走向量检索
//        FilterExpressionBuilder builder = new FilterExpressionBuilder();
//        Supplier<Filter.Expression> expressionSupplier =
//                () -> builder.eq("fileId", String.valueOf(attachment.getId())).build();
//
//        VectorStoreDocumentRetriever retriever =
//                new VectorStoreDocumentRetriever(attachmentVectorStore, 0.4, 10, expressionSupplier);
//
//        List<Document> retrieve = retriever.retrieve(new Query(text));

//        if (retrieve.isEmpty()) {
//            return "The file is still being processed. Please try again in a few seconds.";
//        }
//
        StringBuilder content = new StringBuilder(
                "User uploaded file: " + attachment.getFileName() + ". Here are its contents:\n"
        );
//
//        for (Document document : retrieve) {
//            content.append("\n---\n").append(document.getFormattedContent());
//        }

        return content.toString();
    }
    private String readContentByContentType(InputStream is, String contentType) throws Exception {
        if (contentType == null) {
            return readByTika(is);
        }

        contentType = contentType.toLowerCase();
        if (contentType.startsWith("text/") ||
            contentType.equals("application/json") ||
            contentType.equals("application/xml") ||
            contentType.equals("application/yaml") ||
            contentType.equals("application/x-yaml") ||
            contentType.equals("application/ld+json") ||
            contentType.equals("application/rtf") ||
            contentType.equals("application/javascript") ||
            contentType.equals("application/typescript") ||
            contentType.equals("application/sql") ||
            contentType.equals("application/properties") ||
            contentType.equals("application/java") ||
            contentType.equals("application/python") ||
            contentType.endsWith("+json") ||      // application/geo+json, application/vnd.api+json 等
            contentType.endsWith("+xml")) {       // application/rss+xml, application/atom+xml 等
            return readText(is);
        }
        // 2. PDF
        if (contentType.equals("application/pdf") ||
            contentType.equals("application/x-pdf")) {
            return readPdf(is);
        }
        // 3. Word
        if (contentType.contains("wordprocessingml") ||
            contentType.equals("application/msword") ||
            contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
            contentType.equals("application/vnd.ms-word")) {
            return readDocx(is);
        }
        if (contentType.contains("spreadsheetml") ||
            contentType.equals("application/vnd.ms-excel") ||
            contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
            contentType.equals("application/vnd.ms-excel.sheet.macroEnabled.12") ||
            contentType.equals("application/vnd.ms-excel.sheet.binary.macroEnabled.12")) {
            return readExcel(is);
        }
        // 5. PowerPoint (可选，看你是否需要)
        if (contentType.contains("presentationml") ||
            contentType.equals("application/vnd.ms-powerpoint") ||
            contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
            return readPpt(is,contentType);
        }
        // 6. 兜底：Tika
        return readByTika(is);
    }
    private String readText(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);

    }
    private String readPpt(InputStream is, String contentType) throws Exception {
        if (contentType != null && contentType.contains("presentationml")) {
            return readPptx(is);  // .pptx 新版
        } else {
            return readByTika(is);    // .ppt 老版
        }
    }
    private String readPdf(InputStream is) throws IOException {
        try (PDDocument doc = PDDocument.load(is)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String readDocx(InputStream is) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(is)) {
            return doc.getParagraphs()
                    .stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }
    private String readPptx(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(is)) {
            int slideNumber = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                sb.append("=== 第 ").append(slideNumber++).append("页 ===\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        String text = textShape.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            // 判断是否是标题（通常字体较大或加粗）
                            if (textShape.getTextParagraphs().get(0).getIndentLevel() == 0) {
                                sb.append("# ").append(text).append("\n");
                            } else {
                                sb.append(text).append("\n");
                            }
                        }
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String readExcel(InputStream is) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(is)) {
            StringBuilder sb = new StringBuilder();
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    sb.append(cell.toString()).append("\t");
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }
    private String readByTika(InputStream is) throws Exception {
        Tika tika = new Tika();
        return tika.parseToString(is);
    }
}
