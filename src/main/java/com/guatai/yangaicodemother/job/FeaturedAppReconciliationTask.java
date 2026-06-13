package com.guatai.yangaicodemother.job;

import cn.hutool.core.collection.CollUtil;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.mapper.AppFeaturedApplicationMapper;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.entity.AppFeaturedApplication;
import com.guatai.yangaicodemother.model.enums.FeaturedAppStatusEnum;
import com.guatai.yangaicodemother.service.AppService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 精选应用对账任务：定期将已审核通过但未标记优先级的应用补齐
 */
@Component
@Slf4j
public class FeaturedAppReconciliationTask {

    @Resource
    private AppFeaturedApplicationMapper featuredAppMapper;

    @Resource
    private AppService appService;

    @Resource
    private CacheManager cacheManager;

    /**
     * 每 5 分钟执行一次对账
     */
    @Scheduled(fixedRate = 300_000)
    public void reconcileApprovedApplications() {
        log.info("精选应用对账任务开始执行");

        // 1. 查询所有已通过审核的申请记录
        QueryWrapper queryWrapper = QueryWrapper.create()
            .eq("status", FeaturedAppStatusEnum.APPROVED.getValue());
        List<AppFeaturedApplication> approvedApps = featuredAppMapper.selectListByQuery(queryWrapper);

        if (CollUtil.isEmpty(approvedApps)) {
            return;
        }

        // 2. 获取已通过审核的应用 ID 集合
        Set<Long> approvedAppIds = approvedApps.stream()
            .map(AppFeaturedApplication::getAppId)
            .collect(Collectors.toSet());

        // 3. 查询这些应用中 priority 尚未更新为精选值的
        QueryWrapper appQuery = QueryWrapper.create()
            .in("id", approvedAppIds)
            .ne("priority", AppConstant.GOOD_APP_PRIORITY);
        List<App> unmatchedApps = appService.list(appQuery);

        if (CollUtil.isEmpty(unmatchedApps)) {
            log.info("所有已通过应用均已标记精选优先级，无需处理");
            return;
        }

        // 4. 批量补全 priority
        List<App> updateList = unmatchedApps.stream().map(app -> {
            App update = new App();
            update.setId(app.getId());
            update.setPriority(AppConstant.GOOD_APP_PRIORITY);
            update.setUpdateTime(LocalDateTime.now());
            return update;
        }).collect(Collectors.toList());
        appService.updateBatch(updateList);

        // 5. 清除精选应用缓存
        try {
            Cache cache = cacheManager.getCache("good_app_page");
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.warn("清除精选应用缓存失败", e);
        }

        log.info("精选应用对账任务完成，已修复 {} 个应用的优先级", unmatchedApps.size());
    }
}
