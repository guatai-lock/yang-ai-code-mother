package com.guatai.yangaicodemother.ai;

import dev.langchain4j.service.SystemMessage;

/**
 * AI 应用名称生成服务
 * 独立的 AI 服务接口，专门用于根据用户描述生成应用名称
 */
public interface AiAppNameGeneratorService {

    /**
     * 根据用户描述生成应用名称
     *
     * @param userMessage 用户描述
     * @return 生成的应用名称
     */
    @SystemMessage(fromResource = "prompt/app-name-system-prompt.txt")
    String generateAppName(String userMessage);
}
