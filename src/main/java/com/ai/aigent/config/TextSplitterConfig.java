package com.ai.aigent.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class TextSplitterConfig {

    /**
     * 配置 TokenTextSplitter
     * text-embedding-v4 支持最大 8192 token，这里保守设置为 6000
     */
//    @Bean
//    public TokenTextSplitter tokenTextSplitter() {
//        return new TokenTextSplitter()
//
//            .setMaxTokenCount(6000)          // 最大 token 数（保守值）
//            .setChunkOverlap(200)            // 片段重叠 token 数，保持上下文连贯
//            .setSeparator("\n")              // 分隔符（优先按换行分割）
//            .setSecondarySeparatorList(List.of("。", "！", "？", ".", "!", "?")) // 次分隔符（句子结束符）
//            .setMetadataMode(MetadataMode.ALL)
//            .build();
//    }



}
