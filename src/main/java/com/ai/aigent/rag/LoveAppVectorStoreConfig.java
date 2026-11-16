package com.ai.aigent.rag;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import jakarta.annotation.Resource;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;

@Configuration
public class LoveAppVectorStoreConfig {

    @Resource
    private DocumentReader reader;

   @Bean
    @Qualifier("loveAppVectorStore")
    public VectorStore loveAppVectorStore(

            @Qualifier("postgresqlJdbcTemplate") JdbcTemplate jdbcTemplate,
             DashScopeEmbeddingModel embeddingModel
   ) throws IOException {
       PgVectorStore build = PgVectorStore.builder(jdbcTemplate, embeddingModel)
               .dimensions(768)
               .build();
        return build;
   }

}
