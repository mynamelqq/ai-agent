package com.ai.aigent.service.impl;

import com.ai.aigent.constant.AiCientConstans;
import com.ai.aigent.entity.domain.AiAttachment;
import com.ai.aigent.entity.dto.UploadFileDTO;
import com.ai.aigent.mapper.AiAttachmentMapper;
import com.ai.aigent.service.AiAttachmentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import jakarta.annotation.Resource;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
* @author MECHREVO
* @description 针对表【ai_attachment】的数据库操作Service实现
* @createDate 2026-02-04 22:50:40
*/
@Service
public class AiAttachmentServiceImpl extends ServiceImpl<AiAttachmentMapper, AiAttachment>
    implements AiAttachmentService{
    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 200;
    private static final int VECTOR_BATCH_SIZE = 10;
     @Value("${cos.bucket}")
    private String bucket;
     @Value("${app.cos-enabled:true}")
    private boolean cosEnabled;
     @Autowired
    private COSClient cosClient;
     @Autowired
     private AiAttachmentMapper attachmentMapper;
     @Resource
    @Qualifier("attachmentVectorStore")
    private VectorStore attachmentVectorStore;
    public String uploadFile(MultipartFile file) throws IOException{
        requireCosEnabled();
        String ext = FilenameUtils.getExtension(file.getOriginalFilename());
        String key = "chat/" + UUID.randomUUID() + "." + ext;
        // 元数据
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        PutObjectRequest request = new PutObjectRequest(bucket, key, file.getInputStream(), metadata);
        cosClient.putObject(request);
        String url = "https://" + bucket + ".cos." + cosClient.getClientConfig().getRegion().getRegionName() + ".myqcloud.com/" + key;
        return url;
    }

     @Transactional
     public AiAttachment uploadFileInChat(MultipartFile file, UploadFileDTO dto) throws IOException {
        requireCosEnabled();

        // 生成文件 key
        String ext = FilenameUtils.getExtension(file.getOriginalFilename());
        String key = "chat/" + UUID.randomUUID() + "." + ext;

        // 元数据
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        PutObjectRequest request = new PutObjectRequest(bucket, key, file.getInputStream(), metadata);
        cosClient.putObject(request);
        // 拼接 URL
        String url = "https://" + bucket + ".cos." + cosClient.getClientConfig().getRegion().getRegionName() + ".myqcloud.com/" + key;
        // 写入数据库
        AiAttachment attachment = new AiAttachment();

        attachment.setChatId(dto.getRunId() == null ? dto.getChat_id() : dto.getRunId().toString());
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFileUrl(url);
        attachment.setFileSize(file.getSize());
        attachment.setFileSize(file.getSize());attachment.setFileType(file.getContentType());
        attachment.setConservationId(dto.getConservation_id());
        attachmentMapper.insert(attachment);
        return attachment;
    }
     @Async
    public void asyncEmbedding(AiAttachment att) {
        requireCosEnabled();
        String url = att.getFileUrl();
            String s = ".myqcloud.com/";
            String key = url.substring(url.indexOf(s) + s.length());
            COSObject object = cosClient.getObject(bucket, key);
            try (InputStream is = object.getObjectContent()) {
                String content = readContentByContentType(is, att.getFileType());
                if (content == null || content.isBlank()) {
                    return;
                }
                List<String> chunks = splitSmart(content, CHUNK_SIZE, CHUNK_OVERLAP);
                if (chunks.isEmpty()) {
                    return;
                }
                List<Document> allDocs = new ArrayList<>(chunks.size());
                for (int i = 0; i < chunks.size(); i++) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("fileId", String.valueOf(att.getId()));
                    metadata.put("fileName", att.getFileName());
                    metadata.put("chatId", att.getChatId());
                    metadata.put("conservationId", att.getConservationId());
                    metadata.put("chunkIndex", i);
                    allDocs.add(new Document(String.valueOf(att.getId()) + "_" + i, chunks.get(i), metadata));
                }
                for (int start = 0; start < allDocs.size(); start += VECTOR_BATCH_SIZE) {
                    int end = Math.min(start + VECTOR_BATCH_SIZE, allDocs.size());
                    attachmentVectorStore.add(allDocs.subList(start, end));
                }
                log.debug("Attachment embedding done, fileId=" + att.getId() + ", chunks=" + allDocs.size());
        } catch (Exception e) {
            log.error("Attachment embedding failed, fileId=" + att.getId() + ", key=" + key, e);
        }
    }
    private void requireCosEnabled() {
        if (!cosEnabled) {
            throw new IllegalStateException("COS attachments are disabled by app.cos-enabled=false");
        }
    }
    private List<String> splitSmart(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = Math.min(start + chunkSize, len);
            if (end < len) {
                int newline = text.lastIndexOf('\n', end);
                int punctuation = Math.max(text.lastIndexOf('。', end), text.lastIndexOf('.', end));
                int boundary = Math.max(newline, punctuation);
                if (boundary > start + chunkSize / 2) {
                    end = boundary + 1;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == len) {
                break;
            }
            start = Math.max(0, end - overlap);
        }
        return chunks;
    }
     private String readContentByContentType(InputStream is, String contentType) throws Exception {
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
        return readText(is);
    }
    private String readText(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
                if (sb.length() >= AiCientConstans.MAX_LENGTH_EMBEDDING) break;
            }
        }
        if (sb.length() > AiCientConstans.MAX_CHARS) sb.setLength(AiCientConstans.MAX_CHARS);
        return sb.toString();
    }
    private String readPdf(InputStream is) throws IOException {
        // 使用内存映射文件处理大文件
        try (PDDocument doc = PDDocument.load(is, MemoryUsageSetting.setupTempFileOnly())) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder sb = new StringBuilder(AiCientConstans.MAX_CHARS);

            int totalPages = doc.getNumberOfPages();

            // 快速估算：扫描前几页估算平均字符数
            int samplePages = Math.min(3, totalPages);
            int sampleChars = 0;

            for (int i = 1; i <= samplePages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(doc);
                sampleChars += pageText.length();

                // 收集样本页的内容
                if (sb.length() < AiCientConstans.MAX_CHARS) {
                    sb.append(pageText);
                }
            }

            // 估算剩余所需页数
            int avgCharsPerPage = sampleChars / samplePages;
            int neededPages = Math.min(totalPages,
                (AiCientConstans.MAX_CHARS / avgCharsPerPage) + 1);

            // 处理剩余需要的页数
            if (neededPages > samplePages) {
                stripper.setStartPage(samplePages + 1);
                stripper.setEndPage(neededPages);
                String remainingText = stripper.getText(doc);

                int remainingNeeded = AiCientConstans.MAX_CHARS - sb.length();
                if (remainingNeeded > 0) {
                    sb.append(remainingText.substring(0,
                        Math.min(remainingNeeded, remainingText.length())));
                }
            }

            return sb.toString();
        }
    }
    private String readDocx(InputStream is) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
                if (sb.length() >= AiCientConstans.MAX_CHARS) break;
            }
            if (sb.length() > AiCientConstans.MAX_CHARS) sb.setLength(AiCientConstans.MAX_CHARS);
            return sb.toString();
        }
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
                if (sb.length() >= AiCientConstans.MAX_CHARS) break;
            }
            if (sb.length() > AiCientConstans.MAX_CHARS) sb.setLength(AiCientConstans.MAX_CHARS);
            return sb.toString();
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
                        String text = ((XSLFTextShape) shape).getText();
                        if (text != null && !text.trim().isEmpty()) {
                            sb.append(text).append("\n");
                        }
                    }
                }
                sb.append("\n");
                if (sb.length() >= AiCientConstans.MAX_CHARS) break;
            }
        }
        if (sb.length() > AiCientConstans.MAX_CHARS) sb.setLength(AiCientConstans.MAX_CHARS);
        return sb.toString();
    }
}




