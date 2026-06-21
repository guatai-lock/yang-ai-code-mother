package com.guatai.yangaicodemother.core.pipeline;

import reactor.core.publisher.SignalType;

/**
 * 代码生成管道阶段接口
 * <p>
 * 每个 {@code GenStage} 代表 {@code chatToGenCode()} 流程中的一个独立职责。
 * 通过 {@link org.springframework.core.annotation.Order @Order} 注解控制执行顺序，
 * 通过 {@link #isEnabled(PipelineContext)} 控制条件开关。
 * </p>
 * <p>
 * 设计遵循 Phase 1/2 的相同理念：
 * <ul>
 *   <li>Spring 构造注入自动收集所有 {@code @Component} Stage</li>
 *   <li>{@code @Order} 控制执行顺序</li>
 *   <li>开闭原则：新增阶段只需实现接口并注册为 Bean</li>
 * </ul>
 * </p>
 *
 * @see GenPipeline
 * @see PipelineContext
 */
@FunctionalInterface
public interface GenStage {

    /**
     * 执行阶段逻辑（同步，在 Flux 创建前执行）
     * <p>
     * 在此方法中执行参数校验、数据查询、状态变更等前置逻辑。
     * 抛出 {@link com.guatai.yangaicodemother.exception.BusinessException} 将中止整个管道。
     * </p>
     *
     * @param context 管道上下文（可被阶段修改，为后续阶段传递数据）
     */
    void execute(PipelineContext context);

    /**
     * 清理逻辑（在 Flux 的 {@code doFinally} 中调用）
     * <p>
     * 用于释放线程局部变量、触发异步后置操作等。
     * 默认空实现——不需要清理的阶段无需覆写此方法。
     * </p>
     *
     * @param context    管道上下文
     * @param signalType 完成信号类型：{@link SignalType#ON_COMPLETE}、{@link SignalType#ON_ERROR}、
     *                   {@link SignalType#ON_CANCEL}
     */
    default void cleanup(PipelineContext context, SignalType signalType) {
        // 默认无清理操作
    }

    /**
     * 条件开关：判断当前上下文下是否应执行此阶段
     * <p>
     * 默认返回 {@code true}。需要条件启用的阶段（如基于请求参数）可覆写此方法。
     * 与 Phase 2 {@link com.guatai.yangaicodemother.core.decorator.MessageDecorator#isEnabled}
     * 设计一致。
     * </p>
     *
     * @param context 管道上下文
     * @return {@code true} 执行此阶段，{@code false} 跳过
     */
    default boolean isEnabled(PipelineContext context) {
        return true;
    }
}
