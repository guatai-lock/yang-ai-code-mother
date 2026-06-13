package com.guatai.yangaicodemother.rag.config;

import com.guatai.yangaicodemother.rag.retriever.ConditionalContentRetriever;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RetrievalAugmentor 配置
 *
 * <p>装配完整的 RAG 管道：
 * <ol>
 *   <li>{@link ConditionalContentRetriever} — 条件开关 + 手动 project_type 过滤</li>
 *   <li>{@link DefaultContentInjector} — 知识库参考注入模板</li>
 *   <li>{@link DefaultRetrievalAugmentor} — 编排检索与注入</li>
 * </ol>
 * </p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AugmentorConfig {

    @Resource
    private ContentRetriever contentRetriever;

    @Bean
    public RetrievalAugmentor retrievalAugmentor() {
        // 1. 条件性包装器（控制 RAG 开关 + 按 project_type 手动过滤）
        ConditionalContentRetriever conditionalRetriever = new ConditionalContentRetriever(contentRetriever);

        // 2. 内容注入器：将检索结果嵌入到 system prompt 中
        ContentInjector injector = DefaultContentInjector.builder()
                .promptTemplate(PromptTemplate.from(
                        """
                        === 知识库参考（仅作风格参考，非用户已有代码）===
                        {{contents}}

                        === 用户问题 ===
                        {{userMessage}}
                        """))
                .build();

        // 3. 构建 RetrievalAugmentor
        DefaultRetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(conditionalRetriever)
                .contentInjector(injector)
                .build();

        log.info("RetrievalAugmentor 初始化完成");
        return augmentor;
    }
}
