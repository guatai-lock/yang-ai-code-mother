package com.guatai.yangaicodemother.listener;

import com.guatai.yangaicodemother.event.AppDeletedEvent;
import com.guatai.yangaicodemother.mapper.AppFeaturedApplicationMapper;
import com.guatai.yangaicodemother.model.entity.AppFeaturedApplication;
import com.guatai.yangaicodemother.model.enums.FeaturedAppStatusEnum;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用删除时清理关联的精选申请
 */
@Component
@Slf4j
public class FeaturedAppCleanupEventListener {

    @Resource
    private AppFeaturedApplicationMapper featuredAppMapper;

    @EventListener
    public void onAppDeleted(AppDeletedEvent event) {
        Long appId = event.getAppId();
        log.info("应用删除事件触发精选申请清理，appId: {}", appId);

        try {
            AppFeaturedApplication update = new AppFeaturedApplication();
            update.setStatus(FeaturedAppStatusEnum.CANCELLED.getValue());
            update.setUpdateTime(LocalDateTime.now());

            QueryWrapper query = QueryWrapper.create()
                    .eq("appId", appId)
                    .eq("status", FeaturedAppStatusEnum.PENDING.getValue());

            featuredAppMapper.updateByQuery(update, query);
            log.info("精选申请清理完成，appId: {}", appId);
        } catch (Exception e) {
            log.error("清理精选申请失败，appId: {}", appId, e);
        }
    }
}
