package com.ai.aigent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class LoveAppDocumentReader {
    private final ResourcePatternResolver resolver;

    public LoveAppDocumentReader(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }
    public List<Document>loadDocuments() throws IOException {
        List<Document> documents = new ArrayList<>();
        Resource[] resources = resolver.getResources("classpath:document/*.md");
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            int i = filename.lastIndexOf('-') + 1;
            String status = filename.substring(i, i + 3);
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .withAdditionalMetadata("filename", filename)
                    .withAdditionalMetadata("status",status)
                    .build();
            MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
            documents.addAll(markdownDocumentReader.get());
        }
        return  documents;
    }
}
