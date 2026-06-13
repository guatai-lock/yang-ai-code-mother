package com.guatai.yangaicodemother.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 配置属性
 *
 * <p>绑定 {@code rag.*} 配置前缀，控制 RAG 知识库检索功能的全局行为。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /**
     * RAG 功能总开关（仅控制加载，不影响请求级开关 {@code ragEnabled}）
     */
    private boolean enabled = true;

    /**
     * 检索返回的最大结果数
     */
    private int maxResults = 3;

    /**
     * 最小相似度分数（低于此值的结果将被过滤）
     */
    private double minScore = 0.5;

    /**
     * 向量库 JSON 持久化路径
     */
    private String persistencePath = "tmp/rag_store.json";

    /**
     * 嵌入模型名称（用于日志显示）
     */
    private String embeddingModelName = "BgeSmallZhV15";
}
