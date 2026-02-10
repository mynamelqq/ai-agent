package com.ai.aigent.utils;

import com.ai.aigent.entity.AiAttachment;
import com.alibaba.cloud.ai.document.TextDocumentParser;
import com.alibaba.dashscope.rerank.TextReRankOutput;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import jakarta.annotation.Resource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class AttachmentReaderUtil {
    @Autowired
    private COSClient cosClient;
    public static final long SMALL_FILE_LIMIT = 70 * 1024; // 50KB
    @Resource
    @Qualifier("attachmentVectorStore")
    private VectorStore attachmentVectorStore;
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
    public String readContent(AiAttachment attachment, String messageId, String chatId, String text) {
        String url = attachment.getFileUrl();
        String s = ".myqcloud.com/";
        String key = url.substring(url.indexOf(s) + s.length());
        String fileType = attachment.getFileType();
        Long fileSize = attachment.getFileSize();

        // ✅ 小文件：直接读取全文
        if (fileSize < SMALL_FILE_LIMIT) {
            COSObject object = cosClient.getObject("efrewew-1402637531", key);
            try (InputStream is = object.getObjectContent()) {
                long contentLength = object.getObjectMetadata().getContentLength();
                return "User uploaded file: "+attachment.getFileName()+". Here are its contents:\n"+readContentByContentType(is, fileType);
            } catch (Exception e) {
                throw new RuntimeException("读取 COS 文件失败: " + key, e);
            }
        }

        // ✅ 大文件：走向量检索
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Supplier<Filter.Expression> expressionSupplier =
                () -> builder.eq("fileId", String.valueOf(attachment.getId())).build();

        VectorStoreDocumentRetriever retriever =
                new VectorStoreDocumentRetriever(attachmentVectorStore, 0.4, 10, expressionSupplier);

        List<Document> retrieve = retriever.retrieve(new Query(text));

        if (retrieve.isEmpty()) {
            return "The file is still being processed. Please try again in a few seconds.";
        }

        StringBuilder content = new StringBuilder(
                "User uploaded file: " + attachment.getFileName() + ". Here are its contents:\n"
        );

        for (Document document : retrieve) {
            content.append("\n---\n").append(document.getFormattedContent());
        }

        return content.toString();
    }
     private String readContentByContentType(InputStream is, String contentType) throws Exception {

        if (contentType == null) {
            return readByTika(is);
        }

        contentType = contentType.toLowerCase();

        if (contentType.startsWith("text/plain") || contentType.startsWith("text/markdown")) {
            return readText(is);
        }
        if (contentType.equals("application/pdf")) {
            return readPdf(is);
        }
//        if (contentType.contains("wordprocessingml") || contentType.equals("application/msword")) {
//            return readDocx(is);
//        }
        if (contentType.equals("text/csv")) {
            return readCsv(is);
        }
//        if (contentType.contains("spreadsheetml")) {
//            return readExcel(is);
//        }

        return readByTika(is);
    }
    private String readText(InputStream is) throws IOException {
        String s=new String(is.readAllBytes(), StandardCharsets.UTF_8);
        return s;
    }
        private String readPdf(InputStream is) throws IOException {
        try (PDDocument doc = PDDocument.load(is)) {
            return new PDFTextStripper().getText(doc);
        }
    }

//    private String readDocx(InputStream is) throws IOException {
//        try (XWPFDocument doc = new XWPFDocument(is)) {
//            return doc.getParagraphs()
//                    .stream()
//                    .map(XWPFParagraph::getText)
//                    .collect(Collectors.joining("\n"));
//        }
//    }

    private String readCsv(InputStream is) throws IOException {
        return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
    }

//    private String readExcel(InputStream is) throws IOException {
//        try (Workbook workbook = WorkbookFactory.create(is)) {
//            StringBuilder sb = new StringBuilder();
//            Sheet sheet = workbook.getSheetAt(0);
//            for (Row row : sheet) {
//                for (Cell cell : row) {
//                    sb.append(cell.toString()).append("\t");
//                }
//                sb.append("\n");
//            }
//            return sb.toString();
//        }
//    }
    private String readByTika(InputStream is) throws Exception {
        Tika tika = new Tika();
        return tika.parseToString(is);
    }
}
