package com.guatai.yangaicodemother.rag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 嵌入模型配置
 *
 * <p>提供 {@link BgeSmallZhV15EmbeddingModel} 单例 Bean。
 * 该模型为本地 ONNX 模型，零 API 费用、零网络延迟。
 * 首次实例化时会从 Maven 依赖的 jar 中解压 ONNX 模型到临时目录（耗时约 1-3 秒）。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmbeddingModelConfig {

    @Resource
    private RagProperties ragProperties;

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 ONNX 嵌入模型: {}, 维度=512", ragProperties.getEmbeddingModelName());
        long start = System.currentTimeMillis();
        BgeSmallZhV15EmbeddingModel model = new BgeSmallZhV15EmbeddingModel();
        long elapsed = System.currentTimeMillis() - start;
        log.info("ONNX 嵌入模型初始化完成，耗时: {}ms", elapsed);
        return model;
    }
}
