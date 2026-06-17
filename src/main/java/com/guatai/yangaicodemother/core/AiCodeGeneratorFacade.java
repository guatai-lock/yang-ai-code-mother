package com.guatai.yangaicodemother.core;

import com.guatai.yangaicodemother.ai.AiCodeGeneratorService;
import com.guatai.yangaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.guatai.yangaicodemother.core.decorator.DecorateContext;
import com.guatai.yangaicodemother.core.decorator.MessageDecoratorChain;
import com.guatai.yangaicodemother.core.strategy.CodeGenStrategy;
import com.guatai.yangaicodemother.core.strategy.CodeGenStrategyRegistry;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

/**
 * AI 代码生成外观类，组合生成和保存功能
 * <p>
 * 职责：
 * <ol>
 *   <li>消息富化 — 委派给 {@link MessageDecoratorChain} 装饰器链（Phase 2）</li>
 *   <li>委派具体生成行为给 {@link CodeGenStrategy}（通过 {@link CodeGenStrategyRegistry}）</li>
 * </ol>
 * 新增代码生成类型时无需修改此类，只需添加 {@link CodeGenStrategy} 实现。
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    @Lazy
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private CodeGenStrategyRegistry strategyRegistry;

    @Resource
    private MessageDecoratorChain decoratorChain;

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @param skillNames      启用的技能名称列表（可选）
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum,
                                                   Long appId, List<String> skillNames) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 消息富化 — 委派给装饰器链（Phase 2）
        DecorateContext context = DecorateContext.builder()
                .appId(appId)
                .skillNames(skillNames)
                .build();
        String enrichedMessage = decoratorChain.decorate(userMessage, context);
        // 获取 AI 服务 + 策略
        AiCodeGeneratorService codeGeneratorService = aiCodeGeneratorServiceFactory
                .getAiCodeGeneratorService(appId, codeGenTypeEnum);
        CodeGenStrategy strategy = strategyRegistry.getStrategy(codeGenTypeEnum);
        // 委派给策略执行流式生成
        return strategy.generateStream(codeGeneratorService, enrichedMessage, appId);
    }

    /**
     * 统一入口：根据类型生成并保存代码（非流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @param skillNames      启用的技能名称列表（可选）
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum,
                                     Long appId, List<String> skillNames) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 消息富化 — 委派给装饰器链（Phase 2）
        DecorateContext context = DecorateContext.builder()
                .appId(appId)
                .skillNames(skillNames)
                .build();
        String enrichedMessage = decoratorChain.decorate(userMessage, context);
        // 获取 AI 服务 + 策略（非流式路径仅需默认服务配置）
        AiCodeGeneratorService codeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        CodeGenStrategy strategy = strategyRegistry.getStrategy(codeGenTypeEnum);
        // 委派给策略执行非流式生成 + 保存
        return strategy.generateAndSave(codeGeneratorService, enrichedMessage, appId);
    }
}
