package com.guatai.yangaicodemother.core.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 管道上下文管理器 — 统一编排所有 {@link ContextLifecycle} Bean
 * <p>
 * 职责：
 * <ul>
 *   <li>构造注入自动收集所有 {@code @Component ContextLifecycle} Bean</li>
 *   <li>按 {@code @Order} 排序（与 {@link GenPipeline} 的 Stage 编排一致）</li>
 *   <li>提供统一的 {@link #setup(PipelineContext)} / {@link #restore(PipelineContext)} /
 *       {@link #clear(PipelineContext, SignalType)} 三个生命周期方法</li>
 * </ul>
 * </p>
 * <p>
 * 设计理念：
 * <ul>
 *   <li>{@link GenPipeline} 不再需要知道具体的 ThreadLocal 类型 — 所有上下文通过本管理器统一处理</li>
 *   <li>新增上下文持有者只需实现 {@link ContextLifecycle} 并注册为 Bean，零修改现有代码</li>
 *   <li>单个 handler 异常不影响其他 handler 的执行（仅记录 WARN 日志）</li>
 * </ul>
 * </p>
 *
 * @see ContextLifecycle
 * @see GenPipeline
 */
@Slf4j
@Component
public class PipelineContextManager {

    private final List<ContextLifecycle> handlers;

    /**
     * 构造注入自动收集所有 {@link ContextLifecycle} Bean，按 {@code @Order} 排序后存入不可变列表
     */
    public PipelineContextManager(List<ContextLifecycle> handlers) {
        List<ContextLifecycle> sorted = new ArrayList<>(handlers);
        AnnotationAwareOrderComparator.sort(sorted);
        this.handlers = Collections.unmodifiableList(sorted);
        if (!this.handlers.isEmpty()) {
            log.info("PipelineContextManager 初始化完成，已注册 ContextLifecycle: {}",
                    this.handlers.stream().map(h -> h.getClass().getSimpleName()).toList());
        }
    }

    /**
     * 设置所有上下文（在 {@code runSetup()} 末尾调用）
     * <p>
     * 此时所有 Stage 已执行完毕，PipelineContext 已填充完整。
     * 单个 handler 失败仅记录 WARN，不影响其他 handler。
     * </p>
     *
     * @param context 已填充完整的管道上下文
     */
    public void setup(PipelineContext context) {
        for (ContextLifecycle handler : handlers) {
            try {
                handler.setup(context);
            } catch (Exception e) {
                log.warn("ContextLifecycle.setup 失败: {}", handler.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 恢复所有上下文（在 Flux {@code doOnSubscribe} 中调用）
     * <p>
     * 用于在异步线程中恢复 ThreadLocal 上下文。
     * 单个 handler 失败仅记录 WARN，不影响其他 handler。
     * </p>
     *
     * @param context 管道上下文
     */
    public void restore(PipelineContext context) {
        for (ContextLifecycle handler : handlers) {
            try {
                handler.restore(context);
            } catch (Exception e) {
                log.warn("ContextLifecycle.restore 失败: {}", handler.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 清理所有上下文（在 Flux {@code doFinally} 中调用）
     * <p>
     * 确保 ThreadLocal 被清理，防止内存泄漏。
     * 始终在所有 handler 上执行清理，即使部分 handler 失败。
     * </p>
     *
     * @param context    管道上下文
     * @param signalType 完成信号类型
     */
    public void clear(PipelineContext context, SignalType signalType) {
        for (ContextLifecycle handler : handlers) {
            try {
                handler.clear(context, signalType);
            } catch (Exception e) {
                log.warn("ContextLifecycle.clear 失败: {}", handler.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 获取已注册的 ContextLifecycle 列表（只读视图）
     */
    public List<ContextLifecycle> getHandlers() {
        return handlers;
    }
}
