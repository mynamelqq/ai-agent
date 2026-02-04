package com.ai.aigent.rag;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class CodeAppVectorStoreConfig {



@Bean
@Qualifier("codeAppVectorStore")
public VectorStore codeAppVectorStore(
        @Qualifier("postgresqlJdbcTemplate") JdbcTemplate jdbcTemplate,
        DashScopeEmbeddingModel embeddingModel
        ) {

    PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(1536)                      // ⚠️ 必须和 embedding 模型维度一致
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .vectorTableName("vector_store") // 向量表名
            .initializeSchema(true)               // 启动时自动建表（开发环境建议 true，生产可关）
            .build();

    return vectorStore;
}


}
