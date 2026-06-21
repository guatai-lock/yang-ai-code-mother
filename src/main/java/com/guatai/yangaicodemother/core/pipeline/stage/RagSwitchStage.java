package com.guatai.yangaicodemother.core.pipeline.stage;

import com.guatai.yangaicodemother.core.pipeline.GenStage;
import com.guatai.yangaicodemother.core.pipeline.PipelineContext;
import com.guatai.yangaicodemother.monitor.MonitorContextHolder;
import com.guatai.yangaicodemother.rag.RagSwitchHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.SignalType;

/**
 * RAG 开关管理 Stage
 * <p>
 * 在 {@link #execute(PipelineContext)} 中根据请求参数设置 {@link RagSwitchHolder}，
 * 在 {@link #cleanup(PipelineContext, SignalType)} 中清理 ThreadLocal，
 * 防止内存泄漏。
 * </p>
 * <p>
 * 与 {@link MonitorStage} 一样，这是 ThreadLocal 管理模式的标准实现：
 * execute 中建立 → cleanup 中释放。
 * </p>
 *
 * @see RagSwitchHolder
 * @see MonitorStage
 * @see MonitorContextHolder
 */
@Slf4j
@Component
@Order(20)
public class RagSwitchStage implements GenStage {

    @Override
    public void execute(PipelineContext context) {
        boolean ragActive = context.isRagEnabled();
        RagSwitchHolder.setEnabled(ragActive);
        log.debug("RAG 开关已设置: enabled={}, appId={}", ragActive, context.getAppId());
    }

    @Override
    public void cleanup(PipelineContext context, SignalType signalType) {
        RagSwitchHolder.clear();
        log.debug("RAG 开关已清理, appId={}", context.getAppId());
    }
}
