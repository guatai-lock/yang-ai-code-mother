package com.guatai.yangaicodemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.common.AppConstant;
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
import com.guatai.yangaicodemother.model.enums.FeaturedAppStatusEnum;
import com.guatai.yangaicodemother.model.vo.FeaturedAppApplicationVO;
import com.guatai.yangaicodemother.service.AppService;
import com.guatai.yangaicodemother.service.FeaturedAppApplicationService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PostConstruct;
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

        // 3. 分布式锁 + 事务，防止并发创建重复申请
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
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateApplication(Long applicationId, String action, User loginUser) {
        // 1. 查询申请记录
        AppFeaturedApplication application = getById(applicationId);
        ThrowUtils.throwIf(application == null, ErrorCode.NOT_FOUND_ERROR, "申请记录不存在");

        // 2. 权限校验：只能操作自己的申请
        if (!application.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能操作自己的申请");
        }

        // 3. 根据操作类型处理
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
        transactionTemplate.execute(status -> {
            boolean updateResult = updateBatch(updateList);
            if (!updateResult) {
                status.setRollbackOnly();
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量审核失败");
            }
            return null;
        });

        // 4. 如果通过审核，在事务外批量更新应用优先级
        if (approved && CollUtil.isNotEmpty(approvedAppIds)) {
            batchUpdateAppPriority(approvedAppIds);

            log.info("批量审核通过 {} 个应用，缓存将在过期后自动刷新", approvedAppIds.size());
        }

        log.info("批量审核完成，总数: {}, 成功: {}, 审核结果: {}, 审核人: {}",
            applicationIds.size(), pendingApplications.size(),
            approved ? "通过" : "拒绝",
            adminUser.getId());

        return pendingApplications.size();
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
}
