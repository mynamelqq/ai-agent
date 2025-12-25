package com.ai.aigent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class VectorStoreInitializer  {

    @Resource
    @Qualifier("loveAppVectorStore")
    private VectorStore vectorStore;

    @Resource
    private LoveAppDocumentReader reader;
    @Resource
    private KeywordEnricher enricher;

    public void run(ApplicationArguments args) throws Exception {

        vectorStore.add(enricher.enrich(reader.loadDocuments()));
    }
}