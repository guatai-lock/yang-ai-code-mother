package com.guatai.yangaicodemother.core.pipeline.lifecycle;

import com.guatai.yangaicodemother.core.pipeline.ContextLifecycle;
import com.guatai.yangaicodemother.core.pipeline.PipelineContext;
import com.guatai.yangaicodemother.monitor.MonitorContext;
import com.guatai.yangaicodemother.monitor.MonitorContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.SignalType;

/**
 * {@link MonitorContextHolder} 的生命周期管理
 * <p>
 * 从 {@link PipelineContext#getMonitorContext()} 读取监控上下文，
 * 在以下阶段管理 ThreadLocal：
 * <ul>
 *   <li><b>setup</b> — 设置 {@link MonitorContextHolder}（由 {@code MonitorStage} 预先构建好上下文对象）</li>
 *   <li><b>restore</b> — 在异步线程中恢复</li>
 *   <li><b>clear</b> — 清理 ThreadLocal 防止内存泄漏</li>
 * </ul>
 * </p>
 * <p>
 * 对应 Stage：{@link com.guatai.yangaicodemother.core.pipeline.stage.MonitorStage}（构建 MonitorContext 对象）
 * </p>
 *
 * @see MonitorContextHolder
 * @see com.guatai.yangaicodemother.core.pipeline.stage.MonitorStage
 */
@Slf4j
@Component
@Order(40)
public class MonitorContextLifecycle implements ContextLifecycle {

    @Override
    public void setup(PipelineContext context) {
        MonitorContext monitorContext = context.getMonitorContext();
        if (monitorContext != null) {
            MonitorContextHolder.setContext(monitorContext);
            if (log.isDebugEnabled()) {
                log.debug("MonitorContext 已设置: appId={}, userId={}",
                        monitorContext.getAppId(), monitorContext.getUserId());
            }
        }
    }

    @Override
    public void clear(PipelineContext context, SignalType signalType) {
        MonitorContextHolder.clearContext();
        if (context.getMonitorContext() != null) {
            log.debug("MonitorContext 已清理: appId={}", context.getMonitorContext().getAppId());
        }
    }
}
