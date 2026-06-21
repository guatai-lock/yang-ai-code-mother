package com.guatai.yangaicodemother.core.pipeline.stage;

import com.guatai.yangaicodemother.core.pipeline.GenStage;
import com.guatai.yangaicodemother.core.pipeline.PipelineContext;
import com.guatai.yangaicodemother.monitor.MonitorContext;
import com.guatai.yangaicodemother.monitor.MonitorContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.SignalType;

/**
 * 监控上下文管理 Stage
 * <p>
 * 构建并设置 {@link MonitorContext} 到 {@link MonitorContextHolder}（ThreadLocal），
 * 用于 Micrometer 指标采集。在 cleanup 中清理上下文，防止内存泄漏。
 * </p>
 * <p>
 * 注意：Spring 异步 Servlet 环境下 ThreadLocal 可能丢失上下文。
 * {@code GenPipeline} 的 {@code doOnSubscribe} 会从 {@link PipelineContext#getMonitorContext()}
 * 中恢复上下文，覆盖异步线程路径。
 * </p>
 *
 * @see MonitorContextHolder
 * @see RagSwitchStage
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
        MonitorContextHolder.setContext(monitorContext);

        log.debug("MonitorContext 已设置, appId={}, userId={}",
                context.getAppId(), context.getLoginUser().getId());
    }

    @Override
    public void cleanup(PipelineContext context, SignalType signalType) {
        MonitorContextHolder.clearContext();
        log.debug("MonitorContext 已清理, appId={}", context.getAppId());
    }
}
