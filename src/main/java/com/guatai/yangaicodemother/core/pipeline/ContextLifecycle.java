package com.guatai.yangaicodemother.core.pipeline;

import reactor.core.publisher.SignalType;

/**
 * 上下文生命周期接口 — 管理 ThreadLocal 等执行上下文的设置/恢复/清理
 * <p>
 * 与 {@link GenStage} 分离的独立关注点：
 * <ul>
 *   <li><b>GenStage</b> — 业务逻辑（参数校验、DB 操作、PromptRewrite 等）</li>
 *   <li><b>ContextLifecycle</b> — 执行上下文生命周期（ThreadLocal 的 set/restore/clear）</li>
 * </ul>
 * </p>
 * <p>
 * 三阶段生命周期：
 * <ol>
 *   <li><b>{@link #setup(PipelineContext)}</b> — 在 {@code runSetup()} 末尾调用，
 *       此时所有 Stage 已执行完毕，PipelineContext 已填充完整</li>
 *   <li><b>{@link #restore(PipelineContext)}</b> — 在 Flux {@code doOnSubscribe} 中调用，
 *       用于在异步线程中恢复 ThreadLocal 上下文</li>
 *   <li><b>{@link #clear(PipelineContext, SignalType)}</b> — 在 {@code doFinally} 中调用，
 *       清理 ThreadLocal 防止内存泄漏</li>
 * </ol>
 * </p>
 * <p>
 * 设计原则：
 * <ul>
 *   <li>与 Phase 1/2/3 一致：Spring 构造注入自动收集，{@code @Order} 控制执行顺序</li>
 *   <li>默认方法均为空实现 — 实现类只需覆写需要的方法</li>
 *   <li>setup(restore) → clear 保证配对调用，即使在异常路径下</li>
 * </ul>
 * </p>
 *
 * @see PipelineContextManager
 * @see GenPipeline
 * @see GenStage
 */
@FunctionalInterface
public interface ContextLifecycle {

    /**
     * 设置上下文（在 runSetup 末尾，所有 Stage.execute() 之后执行）
     * <p>
     * 此时 PipelineContext 已包含所有 Stage 的输出，实现类应从中读取数据并设置 ThreadLocal。
     * 默认空实现 — 不需要 setup 的实现类无需覆写。
     * </p>
     *
     * @param context 已填充完整的管道上下文
     */
    default void setup(PipelineContext context) {
        // 默认无操作
    }

    /**
     * 恢复上下文（在 Flux doOnSubscribe 中执行）
     * <p>
     * 用于在异步线程中恢复 ThreadLocal 上下文。
     * 默认行为与 {@link #setup(PipelineContext)} 一致 — 大多数实现可复用。
     * </p>
     *
     * @param context 管道上下文
     */
    default void restore(PipelineContext context) {
        setup(context);
    }

    /**
     * 清理上下文（在 Flux doFinally 中执行）
     * <p>
     * 清理 ThreadLocal 防止内存泄漏。
     * 与 {@link GenStage#cleanup(PipelineContext, SignalType)} 不同：
     * 此方法只负责执行上下文清理（ThreadLocal），不处理业务后置逻辑。
     * </p>
     *
     * @param context    管道上下文
     * @param signalType 完成信号类型
     */
    void clear(PipelineContext context, SignalType signalType);
}
