package com.ai.aigent.rag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
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
public class MarkDownDocumentReaderUtils {
    private final ResourcePatternResolver resolver;

    public MarkDownDocumentReaderUtils(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }
    public List<Document>loadDocumentsMarkdown() throws IOException {
        List<Document> documents = new ArrayList<>();
        Resource[] resources = resolver.getResources("classpath:document/*.md");

        for (Resource resource : resources) {
            String filename = resource.getFilename();

            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeBlockquote(false)
                    .withAdditionalMetadata("filename", filename)
                    .build();
            MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
            documents.addAll(markdownDocumentReader.get());
        }
        return  documents;
    }
    public List<Document>loadDocumentsText() throws IOException {
        TextReader textReader = new TextReader(resolver.getResource("classpath:document/*.txt"));
        List<Document> documents = textReader.get();
        return documents;
    }

}
