package com.guatai.yangaicodemother.core.pipeline;

import com.guatai.yangaicodemother.model.dto.app.ChatToGenCodeRequest;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import com.guatai.yangaicodemother.monitor.MonitorContext;
import lombok.Data;

import java.util.List;

/**
 * 管道上下文 — 在 {@link GenStage} 之间传递数据的可变对象
 * <p>
 * 与 Phase 2 的 {@code DecorateContext}（不可变 record）不同，
 * {@code PipelineContext} 使用可变 {@code @Data} 类，因为各 Stage
 * 需要向上下文中写入中间结果供后续 Stage 使用。
 * </p>
 * <p>
 * 设计原则：
 * <ul>
 *   <li><b>输入</b>（{@link #request}、{@link #loginUser}）：管道启动时固定</li>
 *   <li><b>输出</b>（app、codeGenTypeEnum 等）：由各 Stage 按序填充</li>
 *   <li><b>快照</b>（featuredDeployedApp）：用于 cleanup 阶段的条件判断</li>
 * </ul>
 * </p>
 */
@Data
public class PipelineContext {

    // ────────────────────── 输入（管道启动时设置）──────────────────────

    /** 代码生成请求 */
    private final ChatToGenCodeRequest request;

    /** 登录用户 */
    private final User loginUser;

    // ────────────────────── 输出（由 Stage 填充）──────────────────────

    /** 应用实体（由 ValidationStage 查询并设置） */
    private App app;

    /** 代码生成类型枚举（由 ValidationStage 解析并设置） */
    private CodeGenTypeEnum codeGenTypeEnum;

    /** 重写后的安全提示词（由 PromptRewriteStage 设置） */
    private String safeMessage;

    /** 监控上下文（由 MonitorStage 设置，供 Pipeline doOnSubscribe 在异步线程恢复） */
    private MonitorContext monitorContext;

    /** 是否为精选已部署应用（由 ContentReviewStage 在 execute 时快照，供 cleanup 判断） */
    private boolean featuredDeployedApp;

    // ────────────────────── 便捷访问器 ──────────────────────

    /**
     * 获取应用 ID
     */
    public Long getAppId() {
        return request.getAppId();
    }

    /**
     * 获取原始用户消息（未经 PromptRewrite 重写）
     */
    public String getOriginalMessage() {
        return request.getMessage();
    }

    /**
     * 获取启用的技能名称列表
     */
    public List<String> getSkillNames() {
        return request.getSkillNames();
    }

    /**
     * 判断是否启用了 RAG 知识库
     */
    public boolean isRagEnabled() {
        return Boolean.TRUE.equals(request.getRagEnabled());
    }
}
