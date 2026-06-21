package com.guatai.yangaicodemother.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

/**
 * AI 应用名称生成模型配置
 * 使用多例模式，支持并发调用
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.app-name-chat-model")
@Data
public class AppNameAiModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    /**
     * HTTP 请求超时时间（默认 300 秒，复杂提示词需要更长处理时间）
     */
    private Duration timeout = Duration.ofSeconds(300);

    /**
     * 创建用于应用名称生成的 ChatModel（多例模式）
     */
    @Bean
    @Scope("prototype")
    public ChatModel appNameChatModelPrototype() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .timeout(timeout)
                .build();
    }
}
