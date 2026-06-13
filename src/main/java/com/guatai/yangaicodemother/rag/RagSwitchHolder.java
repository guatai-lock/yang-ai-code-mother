package com.guatai.yangaicodemother.rag;

import lombok.extern.slf4j.Slf4j;

/**
 * RAG 开关持有器（ThreadLocal 模式）
 *
 * <p>与 {@code MonitorContextHolder} 模式一致，通过 ThreadLocal 在请求链路中传递 RAG 开关状态。
 * 在 {@code com.guatai.yangaicodemother.service.impl.AppServiceImpl.chatToGenCode()} 中设置，
 * {@code doFinally} 中清理。</p>
 *
 * <p><b>注意</b>：WebFlux 异步环境下 ThreadLocal 可能丢失，若出现 ragEnabled=true 但未触发 RAG 检索，
 * 优先排查异步线程中的上下文传递。</p>
 */
@Slf4j
public final class RagSwitchHolder {

    private static final ThreadLocal<Boolean> RAG_ENABLED = ThreadLocal.withInitial(() -> false);

    private RagSwitchHolder() {
        // 工具类，禁止实例化
    }

    /**
     * 设置 RAG 开关状态
     */
    public static void setEnabled(boolean enabled) {
        RAG_ENABLED.set(enabled);
        if (enabled) {
            log.debug("RAG 已启用（当前线程: {})", Thread.currentThread().getName());
        }
    }

    /**
     * 检查 RAG 是否启用
     */
    public static boolean isEnabled() {
        return Boolean.TRUE.equals(RAG_ENABLED.get());
    }

    /**
     * 清理当前线程的 RAG 开关状态
     * <p>应在 {@code doFinally} 或 {@code afterCompletion} 中调用，防止内存泄漏。</p>
     */
    public static void clear() {
        RAG_ENABLED.remove();
    }
}
