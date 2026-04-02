package com.ai.aigent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
@SpringBootTest
class DocumentReaderTest {
    @Resource
    private DocumentReader documentReader;
    @Test
    void loadDocuments() throws IOException {

        List<Document> documents = documentReader.loadDocuments();
        System.out.println(documents);
    }
}