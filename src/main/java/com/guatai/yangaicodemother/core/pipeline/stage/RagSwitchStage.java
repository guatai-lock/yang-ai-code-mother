package com.guatai.yangaicodemother.core.pipeline.stage;

import com.guatai.yangaicodemother.core.pipeline.GenStage;
import com.guatai.yangaicodemother.core.pipeline.PipelineContext;
import com.guatai.yangaicodemother.core.pipeline.lifecycle.RagSwitchContextLifecycle;
import com.guatai.yangaicodemother.rag.RagSwitchHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * RAG 开关读取 Stage
 * <p>
 * 从请求参数中读取 RAG 开关状态并记录日志。
 * </p>
 * <p>
 * <b>ThreadLocal 生命周期已迁移到 {@link RagSwitchContextLifecycle}</b>：
 * <ul>
 *   <li>本 Stage 记录 RAG 开关状态到日志（可观测性）</li>
 *   <li>{@link RagSwitchContextLifecycle#setup(PipelineContext)} 设置 {@link RagSwitchHolder}</li>
 *   <li>{@link RagSwitchContextLifecycle#clear(PipelineContext, SignalType)} 清理 ThreadLocal</li>
 *   <li>{@link RagSwitchContextLifecycle#restore(PipelineContext)} 在异步线程中恢复</li>
 * </ul>
 * </p>
 *
 * @see RagSwitchHolder
 * @see RagSwitchContextLifecycle
 */
@Slf4j
@Component
@Order(20)
public class RagSwitchStage implements GenStage {

    @Override
    public void execute(PipelineContext context) {
        boolean ragActive = context.isRagEnabled();
        log.debug("RAG 开关已读取: enabled={}, appId={}", ragActive, context.getAppId());
    }
}
