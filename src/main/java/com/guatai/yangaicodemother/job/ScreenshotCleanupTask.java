package com.guatai.yangaicodemother.job;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

/**
 * 本地临时封面截图清理任务：定期清理未被正常清除的截图目录
 */
@Component
@Slf4j
public class ScreenshotCleanupTask {

    private static final Duration RETENTION_DURATION = Duration.ofHours(1);

    /**
     * 每 30 分钟执行一次清理
     */
    @Scheduled(fixedRate = 1_800_000)
    public void cleanupExpiredScreenshots() {
        String screenshotsDirPath = System.getProperty("user.dir")
                + File.separator + "tmp" + File.separator + "screenshots";
        File screenshotsDir = new File(screenshotsDirPath);

        if (!screenshotsDir.exists() || !screenshotsDir.isDirectory()) {
            return;
        }

        Instant now = Instant.now();
        int deletedCount = 0;

        File[] subDirs = screenshotsDir.listFiles(File::isDirectory);
        if (subDirs == null) {
            return;
        }

        for (File dir : subDirs) {
            try {
                if (isExpired(dir, now)) {
                    FileUtil.del(dir);
                    deletedCount++;
                    log.info("清理过期截图目录: {}", dir.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("清理截图目录失败: {}", dir.getAbsolutePath(), e);
            }
        }

        if (deletedCount > 0) {
            log.info("截图清理任务完成，共清理 {} 个过期目录", deletedCount);
        }
    }

    private boolean isExpired(File dir, Instant now) {
        long lastModified = dir.lastModified();
        if (lastModified <= 0) {
            return false;
        }
        Instant modifiedTime = Instant.ofEpochMilli(lastModified);
        return Duration.between(modifiedTime, now).compareTo(RETENTION_DURATION) > 0;
    }
}
