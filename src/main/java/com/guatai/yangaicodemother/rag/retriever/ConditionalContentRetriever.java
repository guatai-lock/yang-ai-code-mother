package com.guatai.yangaicodemother.rag.retriever;

import com.guatai.yangaicodemother.rag.RagSwitchHolder;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 条件性 ContentRetriever 包装器
 *
 * <p>装饰模式：包装真实的 {@link ContentRetriever}（EmbeddingStoreContentRetriever），
 * 仅在 {@link RagSwitchHolder#isEnabled()} 返回 true 时执行检索。
 * 关闭 RAG 时返回空列表，对现有流程零影响。</p>
 */
@Slf4j
public class ConditionalContentRetriever implements ContentRetriever {

    private final ContentRetriever delegate;

    public ConditionalContentRetriever(ContentRetriever delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (!RagSwitchHolder.isEnabled()) {
            log.trace("RAG 开关已关闭，跳过检索");
            return List.of();
        }
        log.debug("RAG 检索: query={}", query.text());
        List<Content> results = delegate.retrieve(query);
        log.debug("RAG 检索完成: 命中 {} 条结果", results.size());
        return results;
    }
}
