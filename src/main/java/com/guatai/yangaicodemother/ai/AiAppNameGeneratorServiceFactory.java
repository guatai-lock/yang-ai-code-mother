package com.guatai.yangaicodemother.ai;

import com.guatai.yangaicodemother.ai.guardrail.CompositeInputGuardrail;
import com.guatai.yangaicodemother.ai.guardrail.CompositeOutputGuardrail;
import com.guatai.yangaicodemother.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 应用名称生成服务工厂
 * 负责创建独立的应用名称生成 AI 服务实例
 */
@Slf4j
@Configuration
public class AiAppNameGeneratorServiceFactory {

    @Resource
    private CompositeInputGuardrail compositeInputGuardrail;

    /**
     * 创建 AI 应用名称生成服务实例
     * 使用多例模式的 ChatModel，支持并发调用
     */
    public AiAppNameGeneratorService createAiAppNameGeneratorService() {
        // 动态获取多例的 ChatModel，支持并发
        ChatModel chatModel = SpringContextUtil.getBean("appNameChatModelPrototype", ChatModel.class);
        return AiServices.builder(AiAppNameGeneratorService.class)
                .chatModel(chatModel)
                .inputGuardrails(compositeInputGuardrail)
                .outputGuardrails(new CompositeOutputGuardrail())
                .build();
    }

    /**
     * 默认提供一个 Bean（与旧逻辑兼容）
     */
    @Bean
    public AiAppNameGeneratorService aiAppNameGeneratorService() {
        return createAiAppNameGeneratorService();
    }
}
