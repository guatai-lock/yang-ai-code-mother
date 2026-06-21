package com.guatai.yangaicodemother.core.pipeline;

import com.guatai.yangaicodemother.core.AiCodeGeneratorFacade;
import com.guatai.yangaicodemother.core.handler.StreamHandlerExecutor;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.dto.app.ChatToGenCodeRequest;
import com.guatai.yangaicodemother.monitor.MonitorContextHolder;
import com.guatai.yangaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 代码生成管道 — 编排所有 {@link GenStage}，实现 {@code chatToGenCode()} 全流程
 * <p>
 * 三阶段执行模型：
 * <ol>
 *   <li><b>Setup</b> — 按 {@code @Order} 顺序同步执行所有已启用的 Stage</li>
 *   <li><b>Flux 生成</b> — 调用 {@link AiCodeGeneratorFacade} 创建代码流，
 *       并通过 {@link StreamHandlerExecutor} 包装流处理器</li>
 *   <li><b>响应式生命周期</b> — {@code doOnSubscribe} 恢复 ThreadLocal 上下文，
 *       {@code doFinally} 遍历所有 Stage 执行清理</li>
 * </ol>
 * </p>
 * <p>
 * 与 Phase 2 {@code MessageDecoratorChain} 设计一致：
 * <ul>
 *   <li>构造注入自动收集所有 {@code @Component GenStage} Bean</li>
 *   <li>{@link AnnotationAwareOrderComparator} 按 {@code @Order} 排序</li>
 *   <li>排序后存入不可变列表</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class GenPipeline {

    private final List<GenStage> stages;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    @Lazy
    private ChatHistoryService chatHistoryService;

    /**
     * 构造注入自动收集所有 {@link GenStage} Bean，按 {@code @Order} 排序后存入不可变列表
     */
    public GenPipeline(List<GenStage> stages) {
        List<GenStage> sorted = new ArrayList<>(stages);
        AnnotationAwareOrderComparator.sort(sorted);
        this.stages = Collections.unmodifiableList(sorted);
        log.info("GenPipeline 初始化完成，已注册 Stage: {}",
                this.stages.stream().map(s -> s.getClass().getSimpleName()).toList());
    }

    /**
     * 执行完整代码生成管道
     *
     * @param request  代码生成请求
     * @param loginUser 登录用户
     * @return 经过流处理器包装和生命周期管理的代码生成流
     */
    public Flux<String> execute(ChatToGenCodeRequest request, User loginUser) {
        // ── Phase 1: Setup — 同步执行所有 Stage ──
        PipelineContext ctx = new PipelineContext(request, loginUser);
        runSetup(ctx);

        // ── Phase 2: 生成代码流 ──
        Flux<String> flux = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                ctx.getSafeMessage() != null ? ctx.getSafeMessage() : ctx.getOriginalMessage(),
                ctx.getCodeGenTypeEnum(),
                ctx.getAppId(),
                ctx.getSkillNames()
        );

        flux = streamHandlerExecutor.doExecute(flux, chatHistoryService,
                ctx.getAppId(), loginUser, ctx.getCodeGenTypeEnum());

        // ── Phase 3: 响应式生命周期 ──
        return flux
                .doOnSubscribe(s -> {
                    // 恢复 MonitorContext（覆盖异步线程/Lazy Flux 路径）
                    if (ctx.getMonitorContext() != null) {
                        MonitorContextHolder.setContext(ctx.getMonitorContext());
                    }
                })
                .doFinally(signalType -> runCleanup(ctx, signalType));
    }

    /**
     * 获取已注册的 Stage 列表（只读视图）
     */
    public List<GenStage> getStages() {
        return stages;
    }

    // ────────────────────── 私有方法 ──────────────────────

    /**
     * 按顺序执行所有已启用的 Stage
     */
    private void runSetup(PipelineContext ctx) {
        for (GenStage stage : stages) {
            if (stage.isEnabled(ctx)) {
                log.debug("执行 Stage: {}", stage.getClass().getSimpleName());
                stage.execute(ctx);
            } else {
                log.debug("跳过 Stage: {}（未启用）", stage.getClass().getSimpleName());
            }
        }
    }

    /**
     * 遍历所有 Stage 执行清理（在 Flux doFinally 中调用）
     * <p>
     * 单个 Stage cleanup 失败不影响其他 Stage 的清理执行，仅记录 WARN 日志。
     * </p>
     */
    private void runCleanup(PipelineContext ctx, SignalType signalType) {
        for (GenStage stage : stages) {
            try {
                stage.cleanup(ctx, signalType);
            } catch (Exception e) {
                log.warn("Stage cleanup 失败: {}, signalType={}",
                        stage.getClass().getSimpleName(), signalType, e);
            }
        }
    }
}
