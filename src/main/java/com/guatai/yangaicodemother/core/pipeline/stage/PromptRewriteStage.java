package com.guatai.yangaicodemother.core.pipeline.stage;

import com.guatai.yangaicodemother.ai.guardrail.PromptRewriteService;
import com.guatai.yangaicodemother.core.pipeline.GenStage;
import com.guatai.yangaicodemother.core.pipeline.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 提示词重写 Stage
 * <p>
 * 调用 {@link PromptRewriteService#rewrite(String)} 对用户消息进行
 * 安全重写（外轨守卫机制），替换敏感内容，移除注入片段等。
 * </p>
 * <p>
 * 重写后的消息存储在 {@link PipelineContext#setSafeMessage(String)} 中，
 * 供后续 {@code GenPipeline} 在 Flux 创建时使用。
 * </p>
 *
 * @see PromptRewriteService
 */
@Slf4j
@Component
@Order(50)
public class PromptRewriteStage implements GenStage {

    private final PromptRewriteService promptRewriteService;

    public PromptRewriteStage(PromptRewriteService promptRewriteService) {
        this.promptRewriteService = promptRewriteService;
    }

    @Override
    public void execute(PipelineContext context) {
        String originalMessage = context.getOriginalMessage();
        String safeMessage = promptRewriteService.rewrite(originalMessage);
        context.setSafeMessage(safeMessage);
        log.debug("提示词重写完成, appId={}, originalLen={}, safeLen={}",
                context.getAppId(), originalMessage.length(), safeMessage.length());
    }
}
