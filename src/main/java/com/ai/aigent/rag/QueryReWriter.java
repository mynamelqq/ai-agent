package com.ai.aigent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class QueryReWriter {
    private final QueryTransformer transformer;

    QueryReWriter(@Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel)
    {
        ChatClient.Builder builder=ChatClient.builder(dashscopeChatModel);
        this.transformer= RewriteQueryTransformer.builder().chatClientBuilder(builder)
                .build();
    }
    public String doRewrite(String query) {
        Query transform = transformer.transform(new Query(query));
        return transform.text();
    }

}
