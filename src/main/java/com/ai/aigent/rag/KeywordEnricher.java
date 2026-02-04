package com.ai.aigent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KeywordEnricher {
    @Resource
    @Qualifier("dashScopeChatModel")
    private ChatModel dashscpoeChatModel;//基于ai的文档元信息增强器 关键词
    public List<Document> enrich(List<Document> documents) {
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(dashscpoeChatModel, 4);
        return keywordMetadataEnricher.apply(documents);
    }
}
