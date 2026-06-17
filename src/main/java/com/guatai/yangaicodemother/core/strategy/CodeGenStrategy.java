package com.guatai.yangaicodemother.core.strategy;

import com.guatai.yangaicodemother.ai.AiCodeGeneratorService;
import com.guatai.yangaicodemother.core.handler.StreamHandler;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * 代码生成策略 — 每个 CodeGenType 对应一个策略实现
 * <p>
 * 封装该类型完整的生成行为：流式生成、非流式生成、流处理器选择。
 * 新增生成类型时只需添加新的 {@link CodeGenStrategy} 实现并注册为 Spring Bean，
 * 无需修改任何 switch 语句。
 *
 * @see CodeGenStrategyRegistry
 * @see HtmlCodeGenStrategy
 * @see MultiFileCodeGenStrategy
 * @see VueCodeGenStrategy
 */
public interface CodeGenStrategy {

    /**
     * 返回此策略对应的代码生成类型
     */
    CodeGenTypeEnum getType();

    /**
     * 流式生成代码，并在流生命周期中嵌入代码保存/后置处理
     *
     * @param service         AI 代码生成服务
     * @param enrichedMessage 经过富化的用户消息
     * @param appId           应用 ID
     * @return 流式响应（已嵌入 doOnComplete 保存逻辑）
     */
    Flux<String> generateStream(AiCodeGeneratorService service, String enrichedMessage, Long appId);

    /**
     * 非流式生成代码并保存到文件系统
     *
     * @param service         AI 代码生成服务
     * @param enrichedMessage 经过富化的用户消息
     * @param appId           应用 ID
     * @return 保存的目录
     * @throws UnsupportedOperationException 如果该类型不支持非流式生成（如 VUE_PROJECT）
     */
    File generateAndSave(AiCodeGeneratorService service, String enrichedMessage, Long appId);

    /**
     * 创建适合此生成类型的流处理器
     * <p>
     * HTML/MULTI_FILE 返回 {@link com.guatai.yangaicodemother.core.handler.SimpleTextStreamHandler}，
     * VUE_PROJECT 返回 {@link com.guatai.yangaicodemother.core.handler.JsonMessageStreamHandler}。
     *
     * @return 流处理器实例
     */
    StreamHandler createStreamHandler();
}
