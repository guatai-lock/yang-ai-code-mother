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
 */
@Component
@Slf4j
public class AppCleanupEventListener {
    
    @Resource
    private AppCleanupService appCleanupService;
    
    @Async
    @EventListener
    public void onAppDeleted(AppDeletedEvent event) {
        log.info("接收到应用删除事件，appId: {}", event.getAppId());
        
        appCleanupService.cleanupAppData(
            event.getAppId(),
            event.getArchivePath(),
            event.getCodeGenType()
        );
    }
}
