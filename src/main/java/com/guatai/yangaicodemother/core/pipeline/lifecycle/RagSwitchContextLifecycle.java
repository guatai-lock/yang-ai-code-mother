package com.guatai.yangaicodemother.core.pipeline.lifecycle;

import com.guatai.yangaicodemother.core.pipeline.ContextLifecycle;
import com.guatai.yangaicodemother.core.pipeline.PipelineContext;
import com.guatai.yangaicodemother.rag.RagSwitchHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.SignalType;

/**
 * {@link RagSwitchHolder} 的生命周期管理
 * <p>
 * 从 {@link PipelineContext#isRagEnabled()} 读取 RAG 开关状态，
 * 在以下阶段管理 ThreadLocal：
 * <ul>
 *   <li><b>setup</b> — 设置 {@link RagSwitchHolder}</li>
 *   <li><b>restore</b> — 在异步线程中恢复</li>
 *   <li><b>clear</b> — 清理 ThreadLocal 防止内存泄漏</li>
 * </ul>
 * </p>
 * <p>
 * 对应 Stage：{@link com.guatai.yangaicodemother.core.pipeline.stage.RagSwitchStage}（读取请求中的 RAG 开关参数）
 * </p>
 *
 * @see RagSwitchHolder
 * @see com.guatai.yangaicodemother.core.pipeline.stage.RagSwitchStage
 */
@Slf4j
@Component
@Order(20)
public class RagSwitchContextLifecycle implements ContextLifecycle {

    @Override
    public void setup(PipelineContext context) {
        boolean ragActive = context.isRagEnabled();
        RagSwitchHolder.setEnabled(ragActive);
        if (log.isDebugEnabled()) {
            log.debug("RAG 开关已设置: enabled={}, appId={}", ragActive, context.getAppId());
        }
    }

    @Override
    public void clear(PipelineContext context, SignalType signalType) {
        RagSwitchHolder.clear();
        log.debug("RAG 开关已清理, appId={}", context.getAppId());
    }
}
