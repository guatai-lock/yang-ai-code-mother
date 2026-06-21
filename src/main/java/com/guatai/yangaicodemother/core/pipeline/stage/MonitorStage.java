package com.guatai.yangaicodemother.core.pipeline.stage;

import com.guatai.yangaicodemother.core.pipeline.GenStage;
import com.guatai.yangaicodemother.core.pipeline.PipelineContext;
import com.guatai.yangaicodemother.core.pipeline.lifecycle.MonitorContextLifecycle;
import com.guatai.yangaicodemother.monitor.MonitorContext;
import com.guatai.yangaicodemother.monitor.MonitorContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 监控上下文构建 Stage
 * <p>
 * 构建 {@link MonitorContext} 对象并存入 {@link PipelineContext}，
 * 供后续 {@link MonitorContextLifecycle} 设置到 {@link MonitorContextHolder}（ThreadLocal）。
 * </p>
 * <p>
 * <b>ThreadLocal 生命周期已迁移到 {@link MonitorContextLifecycle}</b>：
 * <ul>
 *   <li>本 Stage 只负责构建监控上下文对象</li>
 *   <li>{@link MonitorContextLifecycle#setup(PipelineContext)} 设置 ThreadLocal</li>
 *   <li>{@link MonitorContextLifecycle#clear(PipelineContext, SignalType)} 清理 ThreadLocal</li>
 *   <li>{@link MonitorContextLifecycle#restore(PipelineContext)} 在异步线程中恢复</li>
 * </ul>
 * </p>
 *
 * @see MonitorContext
 * @see MonitorContextHolder
 * @see MonitorContextLifecycle
 */
@Slf4j
@Component
@Order(40)
public class MonitorStage implements GenStage {

    @Override
    public void execute(PipelineContext context) {
        MonitorContext monitorContext = MonitorContext.builder()
                .appId(context.getAppId().toString())
                .userId(context.getLoginUser().getId().toString())
                .build();

        context.setMonitorContext(monitorContext);

        log.debug("MonitorContext 已构建, appId={}, userId={}",
                context.getAppId(), context.getLoginUser().getId());
    }
}
