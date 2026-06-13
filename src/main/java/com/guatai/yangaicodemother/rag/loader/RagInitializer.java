package com.guatai.yangaicodemother.rag.loader;

import com.guatai.yangaicodemother.rag.config.RagProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * RAG 启动初始化器
 *
 * <p>应用启动时执行：
 * <ol>
 *   <li>从 {@code rag_store.json} 恢复向量库（由 {@code VectorStoreConfig} 完成）</li>
 *   <li>调用 {@link CodeCorpusLoader#loadFeaturedAppsCorpus()} 加载精选应用代码</li>
 *   <li>持久化向量库到 JSON 文件</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
public class RagInitializer {

    @Resource
    private RagProperties ragProperties;

    @Resource
    private CodeCorpusLoader codeCorpusLoader;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @PostConstruct
    public void init() {
        if (!ragProperties.isEnabled()) {
            log.info("RAG 功能已禁用，跳过启动初始化");
            return;
        }

        long start = System.currentTimeMillis();
        log.info("RAG 启动初始化开始...");

        try {
            // 1. 加载精选应用语料库（VectorStoreConfig 已在 Bean 初始化时从 JSON 恢复）
            codeCorpusLoader.loadFeaturedAppsCorpus();

            // 2. 持久化向量库到 JSON
            persistStore();

            long elapsed = System.currentTimeMillis() - start;
            log.info("RAG 启动初始化完成，耗时: {}ms", elapsed);
        } catch (Exception e) {
            log.error("RAG 启动初始化失败，将以降级模式运行（不加载语料库）: {}", e.getMessage(), e);
        }
    }

    /**
     * 持久化向量库到 JSON 文件
     */
    public void persistStore() {
        try {
            if (embeddingStore instanceof dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore) {
                @SuppressWarnings("unchecked")
                var memStore = (dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<TextSegment>) embeddingStore;
                String json = memStore.serializeToJson();
                Files.createDirectories(Paths.get(ragProperties.getPersistencePath()).getParent());
                Files.writeString(Paths.get(ragProperties.getPersistencePath()), json);
                log.debug("向量库已持久化到: {}", ragProperties.getPersistencePath());
            }
        } catch (IOException e) {
            log.error("持久化向量库失败: {}", e.getMessage());
        }
    }
}
