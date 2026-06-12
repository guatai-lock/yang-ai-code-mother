package com.guatai.yangaicodemother.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class AiModelMetricsCollector {

    @Resource
    private MeterRegistry meterRegistry;

    // 缓存已创建的指标，避免重复创建（按指标类型分离缓存）
    private final ConcurrentMap<String, Counter> requestCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> tokenCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> responseTimersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> asyncTaskCountersCache = new ConcurrentHashMap<>();

    /**
     * 记录请求次数
     */
    public void recordRequest(String userId, String appId, String modelName, String status) {
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, status);
        Counter counter = requestCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_requests_total")
                        .description("AI模型总请求次数")
                        .tag("user_id", userId)
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .tag("status", status)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * 记录错误（注意：error_message 标签在 Prometheus 中高基数，仅适用于开发/预发环境）
     */
    public void recordError(String userId, String appId, String modelName, String errorMessage) {
        // 使用 errorMessage 的简短摘要作为标签，避免完整错误消息导致标签值爆炸
        String errorSummary = errorMessage != null && errorMessage.length() > 50
                ? errorMessage.substring(0, 50) + "..."
                : errorMessage;
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, errorSummary);
        Counter counter = errorCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_errors_total")
                        .description("AI模型错误次数")
                        .tag("user_id", userId)
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .tag("error_message", errorSummary)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * 记录Token消耗
     */
    public void recordTokenUsage(String userId, String appId, String modelName,
                                 String tokenType, long tokenCount) {
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, tokenType);
        Counter counter = tokenCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_tokens_total")
                        .description("AI模型Token消耗总数")
                        .tag("user_id", userId)
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .tag("token_type", tokenType)
                        .register(meterRegistry)
        );
        counter.increment(tokenCount);
    }

    /**
     * 记录响应时间
     */
    public void recordResponseTime(String userId, String appId, String modelName, Duration duration) {
        String key = String.format("%s_%s_%s", userId, appId, modelName);
        Timer timer = responseTimersCache.computeIfAbsent(key, k ->
                Timer.builder("ai_model_response_duration_seconds")
                        .description("AI模型响应时间")
                        .tag("user_id", userId)
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .register(meterRegistry)
        );
        timer.record(duration);
    }

    /**
     * 记录异步任务执行结果
     *
     * <p>用于追踪虚拟线程执行的后台任务（如截图生成、Vue 构建等）的
     * 成功/失败计数，通过 Prometheus 指标 {@code ai_async_tasks_total} 暴露。</p>
     *
     * @param taskType 任务类型，如 "screenshot"、"vue_build"
     * @param result   执行结果，如 "success"、"failed"、"empty"、"db_update_failed"
     */
    public void recordAsyncTask(String taskType, String result) {
        String key = taskType + ":" + result;
        Counter counter = asyncTaskCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_async_tasks_total")
                        .description("异步任务执行结果计数")
                        .tag("task_type", taskType)
                        .tag("result", result)
                        .register(meterRegistry)
        );
        counter.increment();
    }
}
