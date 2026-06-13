package com.guatai.yangaicodemother.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 向量存储配置
 *
 * <p>提供 {@link InMemoryEmbeddingStore} 单例 Bean，
 * 支持从 JSON 文件恢复持久化的向量数据。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VectorStoreConfig {

    @Resource
    private RagProperties ragProperties;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        InMemoryEmbeddingStore<TextSegment> store;

        // 尝试从 JSON 文件恢复持久化数据
        Path persistenceFile = Paths.get(ragProperties.getPersistencePath());
        if (Files.exists(persistenceFile)) {
            try {
                String json = Files.readString(persistenceFile);
                if (!json.isBlank()) {
                    store = InMemoryEmbeddingStore.fromJson(json);
                    log.info("从 {} 恢复向量库成功", persistenceFile);
                    return store;
                }
            } catch (IOException e) {
                log.warn("从 {} 恢复向量库失败，将创建新的向量库: {}", persistenceFile, e.getMessage());
            }
        }

        store = new InMemoryEmbeddingStore<>();
        log.info("创建新的 InMemoryEmbeddingStore");
        return store;
    }
}
