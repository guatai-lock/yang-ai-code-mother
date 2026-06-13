package com.guatai.yangaicodemother.rag.listener;

import com.guatai.yangaicodemother.event.AppDeployedEvent;
import com.guatai.yangaicodemother.event.AppFeaturedEvent;
import com.guatai.yangaicodemother.event.AppUnfeaturedEvent;
import com.guatai.yangaicodemother.rag.config.RagProperties;
import com.guatai.yangaicodemother.rag.loader.CodeCorpusLoader;
import com.guatai.yangaicodemother.rag.loader.RagInitializer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * RAG 语料库更新事件监听器
 *
 * <p>监听应用精选/部署状态变更事件，自动增量更新向量库。</p>
 */
@Slf4j
@Component
public class RagCorpusUpdateListener {

    @Resource
    private RagProperties ragProperties;

    @Resource
    private CodeCorpusLoader codeCorpusLoader;

    @Resource
    private RagInitializer ragInitializer;

    /**
     * 应用被精选 → 增量加载该应用的代码到向量库
     */
    @EventListener
    public void onAppFeatured(AppFeaturedEvent event) {
        if (!ragProperties.isEnabled()) return;

        Long appId = event.getAppId();
        log.info("收到精选事件，增量加载应用 {} 的代码", appId);
        try {
            codeCorpusLoader.loadSingleApp(appId);
            ragInitializer.persistStore();
            log.info("精选事件处理完成: appId={}", appId);
        } catch (Exception e) {
            log.error("处理精选事件失败: appId={}", appId, e);
        }
    }

    /**
     * 应用取消精选 → 从向量库移除该应用的代码
     */
    @EventListener
    public void onAppUnfeatured(AppUnfeaturedEvent event) {
        if (!ragProperties.isEnabled()) return;

        Long appId = event.getAppId();
        log.info("收到取消精选事件，移除应用 {} 的代码", appId);
        try {
            codeCorpusLoader.removeApp(appId);
            ragInitializer.persistStore();
            log.info("取消精选事件处理完成: appId={}", appId);
        } catch (Exception e) {
            log.error("处理取消精选事件失败: appId={}", appId, e);
        }
    }

    /**
     * 应用部署成功 → 如果是精选应用，更新向量库中的 embedding
     */
    @EventListener
    public void onAppDeployed(AppDeployedEvent event) {
        if (!ragProperties.isEnabled()) return;

        Long appId = event.getAppId();
        log.info("收到部署事件，更新应用 {} 的 embedding（如果是精选应用）", appId);
        try {
            codeCorpusLoader.updateApp(appId);
            ragInitializer.persistStore();
            log.info("部署事件处理完成: appId={}", appId);
        } catch (Exception e) {
            log.error("处理部署事件失败: appId={}", appId, e);
        }
    }
}
