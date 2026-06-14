package com.guatai.yangaicodemother.listener;

import com.guatai.yangaicodemother.event.AppDeletedEvent;
import com.guatai.yangaicodemother.service.AppCleanupService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 应用删除事件监听器
 *
 * <p>异步清理应用关联数据。内置重试机制应对临时性故障（如 Redis 闪断、文件锁定），
 * 重试耗尽后记录关键日志，由运维补偿脚本扫描残留目录。</p>
 */
@Component
@Slf4j
public class AppCleanupEventListener {

    private static final int MAX_RETRIES = 3;

    @Resource
    private AppCleanupService appCleanupService;

    @Async
    @EventListener
    public void onAppDeleted(AppDeletedEvent event) {
        Long appId = event.getAppId();
        log.info("接收到应用删除事件，appId: {}", appId);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                appCleanupService.cleanupAppData(
                        event.getAppId(),
                        event.getArchivePath(),
                        event.getCodeGenType()
                );
                return; // 成功，直接返回
            } catch (Exception e) {
                lastException = e;
                log.warn("清理应用数据失败 (appId={}, attempt={}/{}): {}",
                        appId, attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(1000L * attempt); // 第 1 次失败等 1s、第 2 次失败等 2s……（退避重试，避免瞬间压垮 IO / 存储）

                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("清理重试被中断: appId={}", appId);
                        return;
                    }
                }
            }
        }

        log.error("清理应用数据失败 (appId={}) 已重试 {} 次，需人工介入清理残留",
                appId, MAX_RETRIES, lastException);
    }
}
