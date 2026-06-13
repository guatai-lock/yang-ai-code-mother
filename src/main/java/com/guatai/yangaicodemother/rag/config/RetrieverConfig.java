package com.guatai.yangaicodemother.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 检索器配置
 *
 * <p>提供 {@link EmbeddingStoreContentRetriever} Bean，
 * 基于嵌入模型和向量存储进行语义检索。</p>
 *
 * <p><b>注意</b>：动态过滤（dynamicFilter）在 LangChain4j 1.1.0 中可能不可用，
 * 因此在 {@code ConditionalContentRetriever} 中实现了手动过滤逻辑。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetrieverConfig {

    @Resource
    private RagProperties ragProperties;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 创建基础检索器（无 dynamicFilter，过滤逻辑在 ConditionalContentRetriever 中实现）
     */
    @Bean
    public ContentRetriever contentRetriever() {
        log.info("创建 EmbeddingStoreContentRetriever: maxResults={}, minScore={}",
                ragProperties.getMaxResults(), ragProperties.getMinScore());

        return EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .maxResults(ragProperties.getMaxResults())
                .minScore(ragProperties.getMinScore())
                .build();
    }
}
