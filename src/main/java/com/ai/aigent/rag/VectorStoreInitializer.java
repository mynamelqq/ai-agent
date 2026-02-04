package com.ai.aigent.rag;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class VectorStoreInitializer  {

//    @Resource
//    @Qualifier("codeAppVectorStore")
//    private VectorStore vectorStore;

//    @Resource
//    private CodeAppDocumentReader reader;
//    @Resource
//    private KeywordEnricher enricher;

    public void run(ApplicationArguments args) throws Exception {
//        vectorStore.add(enricher.enrich(reader.loadDocuments()));
    }
}