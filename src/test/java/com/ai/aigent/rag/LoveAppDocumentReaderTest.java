package com.ai.aigent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
@SpringBootTest
class LoveAppDocumentReaderTest {
    @Resource
    private LoveAppDocumentReader loveAppDocumentReader;
    @Test
    void loadDocuments() throws IOException {

        List<Document> documents = loveAppDocumentReader.loadDocuments();
        System.out.println(documents);
    }
}