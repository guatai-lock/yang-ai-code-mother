package com.guatai.yangaicodemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.event.AppFeaturedEvent;
import com.guatai.yangaicodemother.event.AppUnfeaturedEvent;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.exception.ThrowUtils;
import com.guatai.yangaicodemother.mapper.AppFeaturedApplicationMapper;
import com.guatai.yangaicodemother.mapper.AppMapper;
import com.guatai.yangaicodemother.mapper.UserMapper;
import com.guatai.yangaicodemother.model.dto.featuredapp.FeaturedAppQueryRequest;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.entity.AppFeaturedApplication;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.DeployStatusEnum;
import com.guatai.yangaicodemother.model.enums.FeaturedAppStatusEnum;
import com.guatai.yangaicodemother.model.vo.FeaturedAppApplicationVO;
import com.guatai.yangaicodemother.service.AppService;
import com.guatai.yangaicodemother.service.FeaturedAppApplicationService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 精选申请 服务层实现
 */
@Slf4j
@Service
public class FeaturedAppApplicationServiceImpl 
    extends ServiceImpl<AppFeaturedApplicationMapper, AppFeaturedApplication> 
    implements FeaturedAppApplicationService {

    @Resource
    private AppMapper appMapper;

    @Resource
    private AppService appService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private org.springframework.context.ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public Long applyFeaturedApp(Long appId, String reason, User loginUser) {
        // 1. 校验应用是否存在且属于当前用户
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能申请自己的应用");
        }

        // 2. 检查应用是否已经是精选应用
        if (AppConstant.GOOD_APP_PRIORITY.equals(app.getPriority())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该应用已经是精选应用");
        }

        // 3. 检查应用是否已生成代码
        String sourceDirName = app.getCodeGenType() + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        if (!new File(sourceDirPath).exists()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该应用尚未生成代码，无法申请精选");
        }

        // 4. 分布式锁 + 事务，防止并发创建重复申请
        RLock lock = redissonClient.getLock("featured:apply:" + appId);
        lock.lock();
        try {
            return transactionTemplate.execute(status -> {
                // 3. 检查是否已有待审核的申请
                if (hasPendingApplication(appId)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "该应用已有待审核的申请");
                }

                // 4. 检查用户是否已有待审核的申请
                QueryWrapper userPendingQuery = QueryWrapper.create()
                    .eq("userId", loginUser.getId())
                    .eq("status", FeaturedAppStatusEnum.PENDING.getValue());
                long pendingCount = count(userPendingQuery);
                if (pendingCount > 0) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "您已有待审核的申请，请等待审核完成");
                }

                // 5. 创建申请记录
                AppFeaturedApplication application = AppFeaturedApplication.builder()
                    .appId(appId)
                    .userId(loginUser.getId())
                    .reason(reason)
                    .status(FeaturedAppStatusEnum.PENDING.getValue())
                    .build();

                boolean result = save(application);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "申请失败");

                log.info("用户申请精选应用成功，用户ID: {}, 应用ID: {}", loginUser.getId(), appId);
                return application.getId();
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Long requestContentReview(Long appId, User loginUser) {
        // 1. 校验应用是否存在且属于当前用户
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能操作自己的应用");
        }

        // 2. 必须是精选已部署应用
        ThrowUtils.throwIf(!AppConstant.GOOD_APP_PRIORITY.equals(app.getPriority()),
                ErrorCode.OPERATION_ERROR, "该应用不是精选应用");
        ThrowUtils.throwIf(!DeployStatusEnum.ONLINE.getValue().equals(app.getDeployStatus()),
                ErrorCode.OPERATION_ERROR, "该应用尚未部署，请先部署");

        // 3. 分布式锁 + 事务，防止并发创建重复申请
        RLock lock = redissonClient.getLock("featured:apply:" + appId);
        lock.lock();
        try {
            return transactionTemplate.execute(status -> {
                // 4. 检查是否已有待审核的申请
                if (hasPendingApplication(appId)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "该应用已有待审核的内容更新申请，请等待审核完成");
                }

                // 5. 创建新的 PENDING 申请记录（建议的"理由"标识为内容更新）
                AppFeaturedApplication application = AppFeaturedApplication.builder()
                        .appId(appId)
                        .userId(loginUser.getId())
                        .reason("精选应用内容更新，请求重新审核")
                        .status(FeaturedAppStatusEnum.PENDING.getValue())
                        .build();

                boolean result = save(application);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "提交内容审核申请失败");

                log.info("精选应用提交内容更新审核，用户ID: {}, 应用ID: {}", loginUser.getId(), appId);
                return application.getId();
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateApplication(Long applicationId, String action, User loginUser) {
        // 1. 查询申请记录
        AppFeaturedApplication application = getById(applicationId);
        ThrowUtils.throwIf(application == null, ErrorCode.NOT_FOUND_ERROR, "申请记录不存在");

        // 2. 权限校验：只能操作自己的申请
        if (!application.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能操作自己的申请");
        }

        // 3. 获取分布式锁（与 applyFeaturedApp/reviewApplications 共用锁，防止并发）
        RLock lock = redissonClient.getLock("featured:apply:" + application.getAppId());
        lock.lock();
        try {
            // 锁内重新读取，保证数据最新
            application = getById(applicationId);

            // 4. 根据操作类型处理
            if ("CANCEL".equals(action)) {
                // 只能撤销待审核的申请
                if (!FeaturedAppStatusEnum.PENDING.getValue().equals(application.getStatus())) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "只能撤销待审核的申请");
                }

                // 更新状态为已撤销
                AppFeaturedApplication update = new AppFeaturedApplication();
                update.setId(applicationId);
                update.setStatus(FeaturedAppStatusEnum.CANCELLED.getValue());
                boolean result = updateById(update);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "撤销申请失败");

                log.info("用户撤销精选申请，申请ID: {}, 用户ID: {}", applicationId, loginUser.getId());
                return true;
            }

            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的操作类型");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Integer reviewApplications(List<Long> applicationIds, Boolean approved,
                                      String reviewComment, User adminUser) {
        // 限制批量审核数量
        ThrowUtils.throwIf(applicationIds.size() > 100, ErrorCode.PARAMS_ERROR, "批量审核数量不能超过100");

        LocalDateTime reviewTime = LocalDateTime.now();
        String newStatus = approved ?
            FeaturedAppStatusEnum.APPROVED.getValue() :
            FeaturedAppStatusEnum.REJECTED.getValue();

        // 1. 批量查询待审核的申请记录
        QueryWrapper queryWrapper = QueryWrapper.create()
            .in("id", applicationIds)
            .eq("status", FeaturedAppStatusEnum.PENDING.getValue());

        List<AppFeaturedApplication> pendingApplications = list(queryWrapper);

        if (CollUtil.isEmpty(pendingApplications)) {
            log.warn("批量审核：没有找到待审核的申请记录");
            return 0;
        }

        // 2. 构建批量更新列表
        List<AppFeaturedApplication> updateList = new ArrayList<>();
        Set<Long> approvedAppIds = new HashSet<>();

        for (AppFeaturedApplication application : pendingApplications) {
            AppFeaturedApplication update = new AppFeaturedApplication();
            update.setId(application.getId());
            update.setStatus(newStatus);
            update.setReviewerId(adminUser.getId());
            update.setReviewTime(reviewTime);
            update.setReviewComment(reviewComment);
            updateList.add(update);

            if (approved) {
                approvedAppIds.add(application.getAppId());
            }
        }

        // 3. 在事务中批量更新申请记录
        // ⚠️ 注意：此方法没有 @Transactional 注解，TransactionTemplate 独占事务边界。
        //    如果将来在 controller/service 外层添加 @Transactional，会导致 updateBatch
        //    的 SQL 在嵌套事务中无法 flush，审核状态不提交。如需外层事务，请改用 REQUIRES_NEW。
        transactionTemplate.execute(status -> {
            boolean updateResult = updateBatch(updateList);
            if (!updateResult) {
                status.setRollbackOnly();
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量审核失败");
            }
            return null;
        });

        // 4. 如果通过审核，在事务外自动部署 + 批量更新优先级 + 驱逐缓存 + 发布精选事件
        if (approved && CollUtil.isNotEmpty(approvedAppIds)) {

            // 4a. 自动部署（首次精选）或重新部署（内容审核通过）
            //     先部署再更新优先级：确保部署失败时不会造成 priority=99 但未部署的状态不一致
            Set<Long> deployedAppIds = new HashSet<>();
            for (Long appId : approvedAppIds) {
                try {
                    App app = appMapper.selectOneById(appId);
                    if (app == null) continue;

                    boolean isNewFeatured = !DeployStatusEnum.ONLINE.getValue().equals(app.getDeployStatus());
                    String triggerSource = isNewFeatured ? "review-auto-deploy" : "content-review-approve";
                    String deployUrl = appService.internalDeployApp(appId, triggerSource);
                    if (deployUrl != null) {
                        deployedAppIds.add(appId);
                        log.info("审核通过后{}: appId={}", isNewFeatured ? "自动部署" : "重新部署", appId);
                    }
                } catch (Exception e) {
                    log.error("审核通过后部署失败: appId={}", appId, e);
                }
            }

            // 4b. 仅对部署成功的应用更新优先级
            if (CollUtil.isNotEmpty(deployedAppIds)) {
                batchUpdateAppPriority(deployedAppIds);
            }

            // 4c. 驱逐精选应用缓存，确保新通过的应用立即出现在精选列表
            evictGoodAppPageCache();
            // 4d. 发布精选事件（RAG 语料库监听并增量加载，仅对优先已更新的应用）
            for (Long appId : deployedAppIds) {
                applicationContext.publishEvent(new AppFeaturedEvent(this, appId));
            }
            log.info("批量审核通过 {} 个应用，成功部署 {} 个", approvedAppIds.size(), deployedAppIds.size());
        }

        log.info("批量审核完成，总数: {}, 成功: {}, 审核结果: {}, 审核人: {}",
            applicationIds.size(), pendingApplications.size(),
            approved ? "通过" : "拒绝",
            adminUser.getId());

        return pendingApplications.size();
    }

    @Override
    public void unfeatureApp(List<Long> appIds, User adminUser) {
        // 0. 参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(appIds), ErrorCode.PARAMS_ERROR, "应用ID列表不能为空");
        ThrowUtils.throwIf(appIds.size() > 100, ErrorCode.PARAMS_ERROR, "单次最多取消100个应用的精选状态");

        // 1. 查找所有已通过的精选申请
        QueryWrapper query = QueryWrapper.create()
            .in("appId", appIds)
            .eq("status", FeaturedAppStatusEnum.APPROVED.getValue());

        List<AppFeaturedApplication> approvedList = list(query);

        if (CollUtil.isEmpty(approvedList)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "所选应用没有已通过的精选申请");
        }

        // 2. 收集有 APPROVED 记录的应用ID
        Set<Long> actionableAppIds = approvedList.stream()
            .map(AppFeaturedApplication::getAppId)
            .collect(Collectors.toSet());

        // 3. 将所有 APPROVED 记录置为 CANCELLED
        List<AppFeaturedApplication> updateList = approvedList.stream().map(app -> {
            AppFeaturedApplication update = new AppFeaturedApplication();
            update.setId(app.getId());
            update.setStatus(FeaturedAppStatusEnum.CANCELLED.getValue());
            update.setReviewerId(adminUser.getId());
            update.setReviewTime(LocalDateTime.now());
            return update;
        }).collect(Collectors.toList());

        transactionTemplate.execute(status -> {
            boolean updateResult = updateBatch(updateList);
            if (!updateResult) {
                status.setRollbackOnly();
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量取消精选失败");
            }
            return null;
        });

        // 4. 批量重置应用 priority 为 0
        for (Long appId : actionableAppIds) {
            App appUpdate = new App();
            appUpdate.setId(appId);
            appUpdate.setPriority(AppConstant.DEFAULT_APP_PRIORITY);
            appService.updateById(appUpdate);
        }

        // 5. 驱逐缓存
        evictGoodAppPageCache();

        // 6. 发布取消精选事件（RAG 语料库监听并移除 embedding）
        for (Long appId : actionableAppIds) {
            applicationContext.publishEvent(new AppUnfeaturedEvent(this, appId));
        }

        log.info("管理员 {} 已批量取消 {} 个应用的精选状态，并发布取消精选事件", adminUser.getId(), actionableAppIds.size());
    }

    @Override
    public Page<FeaturedAppApplicationVO> listMyApplications(FeaturedAppQueryRequest queryRequest) {
        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
            .eq("userId", queryRequest.getUserId());

        // 可选条件
        if (queryRequest.getAppId() != null) {
            queryWrapper.eq("appId", queryRequest.getAppId());
        }
        if (StrUtil.isNotBlank(queryRequest.getStatus())) {
            queryWrapper.eq("status", queryRequest.getStatus());
        }

        // 按创建时间倒序
        queryWrapper.orderBy("createTime", false);

        // 分页查询
        long pageNum = queryRequest.getPageNum();
        long pageSize = queryRequest.getPageSize();
        Page<AppFeaturedApplication> appPage = page(Page.of(pageNum, pageSize), queryWrapper);

        // 转换为VO
        Page<FeaturedAppApplicationVO> voPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<FeaturedAppApplicationVO> voList = convertToVOList(appPage.getRecords());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public Page<FeaturedAppApplicationVO> listApplicationsByAdmin(FeaturedAppQueryRequest queryRequest) {
        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create();

        // 可选条件
        if (queryRequest.getAppId() != null) {
            queryWrapper.eq("appId", queryRequest.getAppId());
        }
        if (queryRequest.getUserId() != null) {
            queryWrapper.eq("userId", queryRequest.getUserId());
        }
        if (StrUtil.isNotBlank(queryRequest.getStatus())) {
            queryWrapper.eq("status", queryRequest.getStatus());
        }
        if (queryRequest.getReviewerId() != null) {
            queryWrapper.eq("reviewerId", queryRequest.getReviewerId());
        }

        // 按创建时间倒序
        queryWrapper.orderBy("createTime", false);

        // 分页查询
        long pageNum = queryRequest.getPageNum();
        long pageSize = queryRequest.getPageSize();
        Page<AppFeaturedApplication> appPage = page(Page.of(pageNum, pageSize), queryWrapper);

        // 转换为VO
        Page<FeaturedAppApplicationVO> voPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<FeaturedAppApplicationVO> voList = convertToVOList(appPage.getRecords());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public boolean hasPendingApplication(Long appId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
            .eq("appId", appId)
            .eq("status", FeaturedAppStatusEnum.PENDING.getValue());
        return count(queryWrapper) > 0;
    }

    /**
     * 批量更新应用优先级
     */
    private void batchUpdateAppPriority(Set<Long> appIds) {
        if (CollUtil.isEmpty(appIds)) {
            return;
        }

        List<App> updateAppList = new ArrayList<>();
        for (Long appId : appIds) {
            App app = new App();
            app.setId(appId);
            app.setPriority(AppConstant.GOOD_APP_PRIORITY);
            app.setUpdateTime(LocalDateTime.now());
            updateAppList.add(app);
        }
        if (!appService.updateBatch(updateAppList)) {
            log.warn("批量更新应用优先级可能未全部成功，数量: {}", appIds.size());
        } else {
            log.info("批量更新应用优先级成功，数量: {}", appIds.size());
        }
    }

    /**
     * 转换为VO列表
     */
    private List<FeaturedAppApplicationVO> convertToVOList(List<AppFeaturedApplication> records) {
        if (CollUtil.isEmpty(records)) {
            return new ArrayList<>();
        }

        // 批量查询应用信息
        Set<Long> appIds = records.stream()
            .map(AppFeaturedApplication::getAppId)
            .collect(Collectors.toSet());
        Map<Long, App> appMap = appMapper.selectListByIds(appIds).stream()
            .collect(Collectors.toMap(App::getId, app -> app, (a, b) -> a));

        // 批量查询用户信息
        Set<Long> userIds = records.stream()
            .map(AppFeaturedApplication::getUserId)
            .collect(Collectors.toSet());
        records.stream()
            .map(AppFeaturedApplication::getReviewerId)
            .filter(Objects::nonNull)
            .forEach(userIds::add);
        
        Map<Long, User> userMap = userMapper.selectListByIds(userIds).stream()
            .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        // 转换VO
        return records.stream().map(application -> {
            FeaturedAppApplicationVO vo = new FeaturedAppApplicationVO();
            vo.setId(application.getId());
            vo.setAppId(application.getAppId());
            vo.setUserId(application.getUserId());
            vo.setReason(application.getReason());
            vo.setStatus(application.getStatus());
            vo.setReviewComment(application.getReviewComment());
            vo.setReviewerId(application.getReviewerId());
            vo.setReviewTime(application.getReviewTime());
            vo.setCreateTime(application.getCreateTime());
            vo.setUpdateTime(application.getUpdateTime());

            // 设置状态文本
            FeaturedAppStatusEnum statusEnum = FeaturedAppStatusEnum.getEnumByValue(application.getStatus());
            if (statusEnum != null) {
                vo.setStatusText(statusEnum.getText());
            }

            // 设置应用名称
            App app = appMap.get(application.getAppId());
            if (app != null) {
                vo.setAppName(app.getAppName());
            }

            // 设置申请人昵称
            User applicant = userMap.get(application.getUserId());
            if (applicant != null) {
                vo.setUserName(applicant.getUserName());
            }

            // 设置审核人昵称
            if (application.getReviewerId() != null) {
                User reviewer = userMap.get(application.getReviewerId());
                if (reviewer != null) {
                    vo.setReviewerName(reviewer.getUserName());
                }
            }

            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 驱逐精选应用列表缓存
     */
    private void evictGoodAppPageCache() {
        try {
            Cache cache = cacheManager.getCache("good_app_page");
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.warn("清除精选应用缓存失败", e);
        }
    }
}
