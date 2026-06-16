package com.guatai.yangaicodemother.service.impl;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.ai.AiAppNameGeneratorService;
import com.guatai.yangaicodemother.ai.AiAppNameGeneratorServiceFactory;
import com.guatai.yangaicodemother.ai.AiCodeGenTypeRoutingService;
import com.guatai.yangaicodemother.ai.AiCodeGenTypeRoutingServiceFactory;
import com.guatai.yangaicodemother.ai.guardrail.PromptRewriteService;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.core.AiCodeGeneratorFacade;
import com.guatai.yangaicodemother.core.builder.VueProjectBuilder;
import com.guatai.yangaicodemother.core.handler.StreamHandlerExecutor;
import com.guatai.yangaicodemother.event.AppDeployedEvent;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.exception.ThrowUtils;
import com.guatai.yangaicodemother.event.AppDeletedEvent;
import com.guatai.yangaicodemother.model.dto.app.AppAddRequest;
import com.guatai.yangaicodemother.model.dto.app.AppQueryRequest;
import com.guatai.yangaicodemother.model.dto.app.ChatToGenCodeRequest;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import com.guatai.yangaicodemother.model.enums.DeployStatusEnum;
import com.guatai.yangaicodemother.model.vo.AppVO;
import com.guatai.yangaicodemother.model.vo.DeployStatusVO;
import com.guatai.yangaicodemother.model.vo.UserVO;
import com.guatai.yangaicodemother.monitor.AiModelMetricsCollector;
import com.guatai.yangaicodemother.monitor.MonitorContext;
import com.guatai.yangaicodemother.monitor.MonitorContextHolder;
import com.guatai.yangaicodemother.rag.RagSwitchHolder;
import com.guatai.yangaicodemother.service.ChatHistoryService;
import com.guatai.yangaicodemother.service.FeaturedAppApplicationService;
import com.guatai.yangaicodemother.service.ScreenshotService;
import com.guatai.yangaicodemother.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.mapper.AppMapper;
import com.guatai.yangaicodemother.service.AppService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
/**
 * 应用 服务层实现。
 *
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService {
    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    @Lazy
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Resource
    private AiAppNameGeneratorServiceFactory aiAppNameGeneratorServiceFactory;

    @Resource
    private RedissonClient redissonClient;
    
    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private PromptRewriteService promptRewriteService;

    @Resource
    @Lazy
    private FeaturedAppApplicationService featuredAppApplicationService;

    @Resource
    private AiModelMetricsCollector metricsCollector;

    @Resource
    @Qualifier("virtualThreadExecutor")
    private ExecutorService virtualThreadExecutor;

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 使用 AI 智能生成应用名称
        String appName = generateAppNameByAI(initPrompt);
        app.setAppName(appName);
        // 互轨机制：重写路由提示词（移除风险内容后再进行 AI 路由判断）
        String safeInitPrompt = promptRewriteService.rewrite(initPrompt);
        // 使用 AI 智能选择代码生成类型(多例模式)
        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = routingService.routeCodeGenType(safeInitPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题（批量查询代替循环查询）
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }
    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        String deployStatus = appQueryRequest.getDeployStatus();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("deployStatus", deployStatus)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }
    @Override
    public Flux<String> chatToGenCode(ChatToGenCodeRequest request, User loginUser) {
        // 1. 参数校验
        Long appId = request.getAppId();
        String message = request.getMessage();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 2.5 捕获当前应用状态，用于 doFinally 中判断是否触发内容审核
        Integer currentPriority = app.getPriority();
        String currentDeployStatus = app.getDeployStatus();
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        // 5. 设置 RAG 开关（默认关闭，仅当 ragEnabled=true 时启用）
        boolean ragActive = Boolean.TRUE.equals(request.getRagEnabled());
        RagSwitchHolder.setEnabled(ragActive);
        // 6. 通过校验后，添加用户消息到对话历(保存到数据库)
        chatHistoryService.addChatMessage(appId, message,
                ChatHistoryMessageTypeEnum.USER.getValue(),
                loginUser.getId());
        // 6.5 递增应用对话轮次
        App roundUpdate = new App();
        roundUpdate.setId(appId);
        roundUpdate.setConversationRound(app.getConversationRound() == null ? 1 : app.getConversationRound() + 1);
        this.updateById(roundUpdate);
        // 7. 构建并设置监控上下文
        //    inline 设置：覆盖 Hot Flux（HTML/MULTI_FILE），listener.onRequest 在代理调用时同步触发（请求线程）
        //    doOnSubscribe 设置（备份）：覆盖 Lazy Flux（VUE_PROJECT），listener.onRequest 在订阅时触发
        MonitorContext monitorContext = MonitorContext.builder()
                .appId(appId.toString())
                .userId(loginUser.getId().toString())
                .build();
        MonitorContextHolder.setContext(monitorContext);
        // 8. 互轨机制：提示词重写（外轨 — 主动修复）
        // 将原始消息保存到对话历史后，使用重写后的安全版本调用 AI
        String safeMessage = promptRewriteService.rewrite(message);
        // 9. 调用 AI 生成代码（流式）
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(safeMessage, codeGenTypeEnum, appId, request.getSkillNames());
    // 10. 收集 AI 响应内容并在完成后记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService,
                appId, loginUser, codeGenTypeEnum)
                .doOnSubscribe(s ->
                        // 在订阅线程上设置监控上下文（覆盖 Lazy Flux 路径：VUE_PROJECT）
                        MonitorContextHolder.setContext(monitorContext)
                )
                .doFinally(
                        signalType -> {
                            // 清理监控上下文(无论成功/失败/取消)
                            MonitorContextHolder.clearContext();
                            // 清理 RAG 开关状态
                            RagSwitchHolder.clear();

                            // 精选已部署应用 → 代码生成成功后提交内容重新审核，旧版本继续在线
                            // 仅在 ON_COMPLETE 时触发（ON_ERROR / ON_CANCEL 不创建虚假的审核申请）
                            if (signalType == SignalType.ON_COMPLETE
                                    && AppConstant.GOOD_APP_PRIORITY.equals(currentPriority)
                                    && DeployStatusEnum.ONLINE.getValue().equals(currentDeployStatus)) {
                                log.info("精选应用代码生成完成，异步提交内容重新审核: appId={}", appId);
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        featuredAppApplicationService.requestContentReview(appId, loginUser);
                                    } catch (BusinessException e) {
                                        // 已有待审核申请等预期内的异常，只记日志
                                        log.info("精选应用提交内容审核跳过: appId={}, reason={}", appId, e.getMessage());
                                    } catch (Exception e) {
                                        log.error("精选应用提交内容审核失败: appId={}", appId, e);
                                    }
                                }, virtualThreadExecutor);
                            }
                        }
                )
                ;
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 2. 分布式锁（与 deployOffline/deployOnline 共用锁 key，防止并发冲突）
        String lockKey = "app:deploy:lock:" + appId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(3, -1, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作频繁，请稍后重试");
            }

            // 3. 查询应用 + 权限校验
            App app = validateAppPermission(appId, loginUser);

            // 4. 状态校验：禁止在 DEPLOYING 状态下部署
            if (DeployStatusEnum.DEPLOYING.getValue().equals(app.getDeployStatus())) {
                throw new BusinessException(ErrorCode.DEPLOY_STATUS_ERROR, "应用正在部署中，请稍后操作");
            }

            // 5. 检查源目录是否存在
            String sourceDirName = app.getCodeGenType() + "_" + appId;
            String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
            File sourceDir = new File(sourceDirPath);
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
            }

            // 6. 执行部署核心逻辑
            return executeDeploy(app, sourceDir);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 内部部署：用户无感的精选应用自动部署/重新部署
     * <p>
     * 与 {@link #deployApp(Long, User)} 的区别：
     * <ul>
     *   <li>不校验用户权限（由调用方保证触发时机合法）</li>
     *   <li>DEPLOYING 状态静默跳过而非抛出异常</li>
     *   <li>代码目录不存在时静默跳过</li>
     *   <li>所有异常内部消化，不传播到调用方</li>
     * </ul>
     *
     * @param appId        应用 ID
     * @param triggerSource 触发来源标识（用于日志追踪，如 "review-auto-deploy"、"content-review-approve"）
     * @return 部署访问 URL，跳过部署时返回 null
     */
    @Override
    public String internalDeployApp(Long appId, String triggerSource) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");

        String lockKey = "app:deploy:lock:" + appId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(3, -1, TimeUnit.SECONDS)) {
                log.warn("内部部署获取锁失败 (triggerSource={}): appId={}", triggerSource, appId);
                return null;
            }

            App app = this.getById(appId);
            if (app == null) {
                log.warn("内部部署失败，应用不存在: appId={}", appId);
                return null;
            }

            // 跳过 DEPLOYING 状态
            if (DeployStatusEnum.DEPLOYING.getValue().equals(app.getDeployStatus())) {
                log.warn("应用正在部署中，跳过内部部署: appId={}", appId);
                return null;
            }

            // 检查代码目录
            String sourceDirPath = app.getCodeGenType() + "_" + appId;
            File sourceDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirPath);
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                log.warn("代码目录不存在，跳过内部部署: appId={}", appId);
                return null;
            }

            log.info("开始内部部署 (triggerSource={}): appId={}", triggerSource, appId);
            return executeDeploy(app, sourceDir);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("内部部署被中断: appId={}", appId, e);
            return null;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 执行部署核心逻辑（不包含权限校验和分布式锁）
     * <p>
     * 由 {@link #deployApp(Long, User)} 和 {@link #internalDeployApp(Long, String)} 共用。
     * 处理：Vue 构建 → 文件复制 → DB 更新 → 事件发布 → 异步截图。
     *
     * @param app      应用实体（必须包含 id、codeGenType、deployKey 字段）
     * @param sourceDir 代码源目录
     * @return 部署访问 URL
     */
    private String executeDeploy(App app, File sourceDir) {
        Long appId = app.getId();
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);

        // 1. 复用或生成 deployKey
        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }

        // 2. Vue 项目特殊处理：执行构建
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDir.getAbsolutePath());
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");

            File distDir = new File(sourceDir, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }

        // 3. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }

        // 4. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        updateApp.setDeployStatus(DeployStatusEnum.ONLINE.getValue());

        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            updateApp.setLastBuildTime(LocalDateTime.now());
        }

        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");

        // 5. 发布部署事件（RAG 语料库监听此事件更新精选应用的 embedding）
        applicationContext.publishEvent(new AppDeployedEvent(this, appId, deployKey, codeGenType));
        log.debug("已发布部署事件: appId={}, deployKey={}", appId, deployKey);

        // 6. 异步截图
        String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }
    /**
     * 删除应用时关联删除对话历史和AI生成文件
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }

        // 获取分布式锁（与 deployOffline/deployOnline 共用锁 key，防止并发冲突）
        String lockKey = "app:deploy:lock:" + appId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(3, -1, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作频繁，请稍后重试");
            }

            // 1. 查询应用信息（用于状态校验和事件发布）
            App app = this.getById(appId);
            ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

            // 2. 状态校验：只有 OFFLINE/null 状态可删除
            validateDeletePermission(app);

            // 3. 删除关联的对话历史
            try {
                chatHistoryService.deleteByAppId(appId);
            } catch (Exception e) {
                log.error("删除应用关联对话历史失败: {}", e.getMessage());
            }

            // 4. 重置精选优先级（必须在逻辑删除前执行，避免 MyBatis-Flex 逻辑删除过滤）
            App priorityReset = new App();
            priorityReset.setId(appId);
            priorityReset.setPriority(AppConstant.DEFAULT_APP_PRIORITY);
            this.updateById(priorityReset);

            // 5. 删除应用（逻辑删除）
            boolean result = super.removeById(id);

            // 6. 发布删除事件，触发异步文件清理
            if (result && app != null) {
                applicationContext.publishEvent(new AppDeletedEvent(this, app));
                log.info("已发布应用删除事件，appId: {}", appId);
            }

            // 7. 驱逐精选应用缓存，确保列表不包含已删除应用
            evictGoodAppPageCache();

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    /**
     * 校验应用删除权限
     * 
     * 规则：
     * - ONLINE：拒绝删除，需先下线
     * - DEPLOYING：拒绝删除，避免文件锁冲突
     * - OFFLINE：允许删除
     * - null：允许删除（兼容旧数据）
     */
    private void validateDeletePermission(App app) {
        String status = app.getDeployStatus();
        
        // 在线状态：拒绝删除
        if (DeployStatusEnum.ONLINE.getValue().equals(status)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, 
                    "应用处于在线状态，请先下线后再删除");
        }
        
        // 部署中：拒绝删除
        if (DeployStatusEnum.DEPLOYING.getValue().equals(status)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, 
                    "应用正在部署中，请稍后再试");
        }
        
        // OFFLINE 或 null：允许删除
        log.debug("应用删除状态校验通过，appId: {}, status: {}", app.getId(), status);
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * <p>使用虚拟线程执行器执行截图任务，不阻塞调用方。
     * 通过 {@link CompletableFuture} 支持调用方追踪执行状态。
     * 截图成功/失败通过 {@link AiModelMetricsCollector#recordAsyncTask} 上报 Prometheus 指标。</p>
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     * @return 异步任务句柄，可用于追踪截图是否完成
     */
    @Override
    public CompletableFuture<Void> generateAppScreenshotAsync(Long appId, String appUrl) {
        return CompletableFuture.runAsync(() -> {
            try {
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
                if (StrUtil.isBlank(screenshotUrl)) {
                    log.warn("应用 {} 截图生成结果为空，跳过封面更新", appId);
                    metricsCollector.recordAsyncTask("screenshot", "empty");
                    return;
                }
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(screenshotUrl);
                boolean updated = this.updateById(updateApp);
                if (!updated) {
                    log.error("更新应用 {} 封面字段失败", appId);
                    metricsCollector.recordAsyncTask("screenshot", "db_update_failed");
                } else {
                    metricsCollector.recordAsyncTask("screenshot", "success");
                }
            } catch (Exception e) {
                log.error("应用 {} 截图生成失败: {}", appId, e.getMessage(), e);
                metricsCollector.recordAsyncTask("screenshot", "failed");
            }
        }, virtualThreadExecutor);
    }
    /**
     * 使用 AI 根据 initPrompt 生成应用名称
     *
     * @param initPrompt 初始化描述
     * @return 生成的应用名称
     */
    private String generateAppNameByAI(String initPrompt) {
        try {
            // 使用独立的应用名称生成 AI 服务实例（与其他 AI 服务隔离）
            AiAppNameGeneratorService aiService = aiAppNameGeneratorServiceFactory.createAiAppNameGeneratorService();
            String generatedName = aiService.generateAppName(initPrompt);

            // 校验生成的名称是否合法
            if (StrUtil.isBlank(generatedName) || generatedName.length() > 50) {
                log.warn("AI 生成的应用名称不合法，使用降级方案: {}", generatedName);
                return getDefaultAppName(initPrompt);
            }

            return generatedName.trim();
        } catch (Exception e) {
            log.error("AI 生成应用名称失败，使用降级方案: {}", e.getMessage());
            return getDefaultAppName(initPrompt);
        }
    }

    /**
     * 降级方案：使用 initPrompt 前 12 位（保持原有逻辑）
     *
     * @param initPrompt 初始化描述
     * @return 默认应用名称
     */
    private String getDefaultAppName(String initPrompt) {
        return initPrompt.substring(0, Math.min(initPrompt.length(), 12));
    }

    @Override
    public String deployOffline(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 2. 分布式锁（防止并发冲突）
        String lockKey = "app:deploy:lock:" + appId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 3. 获取锁（等待3秒，持有10秒）
            if (!lock.tryLock(3, -1, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作频繁，请稍后重试");
            }

            // 4. 查询应用 + 权限校验
            App app = validateAppPermission(appId, loginUser);

            // 5. 状态校验
            validateStatusTransition(app.getDeployStatus(), DeployStatusEnum.OFFLINE);

            // 6. 精选应用保护：禁止直接下线
            if (AppConstant.GOOD_APP_PRIORITY.equals(app.getPriority())) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "该应用是精选应用，无法直接下线。请先取消精选后再操作");
            }

            // 8. 先保存文件操作所需的旧值（DB 更新后 deployKey 会被清空）
            String oldDeployKey = app.getDeployKey();
            CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());

            // 9. 更新数据库（先于文件操作，保证状态一致）
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setDeployStatus(DeployStatusEnum.OFFLINE.getValue());
            updateApp.setDeployKey(null);
            updateApp.setEditTime(LocalDateTime.now());
            boolean result = this.updateById(updateApp);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "应用下线失败");
            // ↑ DB 已提交，状态为 OFFLINE。
            //   即使后续文件操作失败，也比"DB 仍显示 ONLINE 但文件已丢失"更安全

            // 10. 文件归档（DB 已一致，文件操作失败不影响状态正确性）
            String archivePath = null;
            if (StrUtil.isNotBlank(oldDeployKey)) {
                if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
                    archivePath = archiveVueProject(oldDeployKey, appId);
                } else {
                    archivePath = archiveDeployDirectory(oldDeployKey, appId);
                }

                // 清理旧部署目录
                String oldDeployDir = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + oldDeployKey;
                FileUtil.del(new File(oldDeployDir));

                // 回填 archivePath
                if (StrUtil.isNotBlank(archivePath)) {
                    App pathUpdate = new App();
                    pathUpdate.setId(appId);
                    pathUpdate.setArchivePath(archivePath);
                    this.updateById(pathUpdate);
                }
            }

            log.info("应用下线成功，appId: {}, archivePath: {}", appId, archivePath);
            return "应用已下线，归档路径：" + archivePath;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public String deployOnline(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 2. 分布式锁
        String lockKey = "app:deploy:lock:" + appId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 3. 获取锁
            if (!lock.tryLock(3,-1, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作频繁，请稍后重试");
            }

            // 4. 查询应用 + 权限校验
            App app = validateAppPermission(appId, loginUser);

            // 5. 状态校验
            validateStatusTransition(app.getDeployStatus(), DeployStatusEnum.ONLINE);

            // 6. 保存归档路径（DB 更新后会清空）
            String archivePath = app.getArchivePath();
            String deployKey = RandomUtil.randomString(6);
            CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());

            // 7. 文件恢复优先执行（COPY 而非 MOVE，保留归档副本）
            //    即使后续 DB 更新失败，文件已在部署目录中，状态仍为 OFFLINE 可重试
            if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
                restoreAndBuildVueProject(archivePath, deployKey, appId);
            } else {
                restoreDeployDirectory(archivePath, deployKey);
            }

            // 8. 更新数据库
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setDeployStatus(DeployStatusEnum.ONLINE.getValue());
            updateApp.setDeployKey(deployKey);
            updateApp.setDeployedTime(LocalDateTime.now());
            updateApp.setArchivePath(null);
            updateApp.setEditTime(LocalDateTime.now());

            if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
                updateApp.setLastBuildTime(LocalDateTime.now());
            }

            boolean result = this.updateById(updateApp);
            if (!result) {
                // 补偿：DB 更新失败，清理部署目录，保留归档以便重试
                FileUtil.del(new File(AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey));
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "应用上线失败");
            }

            // 9. DB 更新成功后清理归档目录（文件已在部署目录中）
            if (StrUtil.isNotBlank(archivePath)) {
                try {
                    FileUtil.del(new File(archivePath));
                } catch (Exception e) {
                    log.warn("清理归档目录失败，archivePath: {}", archivePath, e);
                }
            }

            // 10. 返回可访问的 URL
            String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);

            // 11. 异步生成截图
            generateAppScreenshotAsync(appId, appDeployUrl);

            log.info("应用上线成功，appId: {}, deployKey: {}, url: {}", appId, deployKey, appDeployUrl);
            return appDeployUrl;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public DeployStatusVO getDeployStatus(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");

        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        DeployStatusVO vo = new DeployStatusVO();
        vo.setAppId(app.getId());
        vo.setAppName(app.getAppName());
        vo.setDeployStatus(app.getDeployStatus());
        vo.setDeployKey(app.getDeployKey());

        // 设置状态文本
        DeployStatusEnum statusEnum = DeployStatusEnum.getEnumByValue(app.getDeployStatus());
        if (statusEnum != null) {
            vo.setDeployStatusText(statusEnum.getText());
        }

        // 如果在线，构建访问 URL
        if (DeployStatusEnum.ONLINE.getValue().equals(app.getDeployStatus()) 
                && StrUtil.isNotBlank(app.getDeployKey())) {
            vo.setDeployedUrl(String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, app.getDeployKey()));
        }

        vo.setDeployedTime(app.getDeployedTime());
        vo.setArchivePath(app.getArchivePath());
        vo.setLastBuildTime(app.getLastBuildTime());
        vo.setCodeGenType(app.getCodeGenType());

        return vo;
    }

    @Override
    public App getByDeployKey(String deployKey) {
        if (StrUtil.isBlank(deployKey)) {
            return null;
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("deployKey", deployKey)
                .eq("deployStatus", DeployStatusEnum.ONLINE.getValue());

        return this.getOne(queryWrapper);
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 校验应用权限（仅创建者可操作）
     */
    private App validateAppPermission(Long appId, User loginUser) {
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该应用");
        }

        return app;
    }

    /**
     * 校验状态流转合法性
     */
    private void validateStatusTransition(String currentStatus, DeployStatusEnum targetStatus) {
        DeployStatusEnum currentEnum = DeployStatusEnum.getEnumByValue(currentStatus);

        // 未部署状态只能转为 ONLINE（首次部署走 deployApp）
        if (currentEnum == null) {
            if (targetStatus != DeployStatusEnum.ONLINE) {
                throw new BusinessException(ErrorCode.DEPLOY_STATUS_ERROR,
                        "应用尚未部署，不能转为" + targetStatus.getText());
            }
            return;
        }

        // OFFLINE → ONLINE/DEPLOYING
        if (currentEnum == DeployStatusEnum.OFFLINE) {
            if (targetStatus != DeployStatusEnum.ONLINE) {
                throw new BusinessException(ErrorCode.DEPLOY_STATUS_ERROR, 
                        "离线状态只能转为在线，不能转为" + targetStatus.getText());
            }
            return;
        }

        // ONLINE → OFFLINE
        if (currentEnum == DeployStatusEnum.ONLINE) {
            if (targetStatus != DeployStatusEnum.OFFLINE) {
                throw new BusinessException(ErrorCode.DEPLOY_STATUS_ERROR, 
                        "在线状态只能转为离线，不能转为" + targetStatus.getText());
            }
            return;
        }

        // DEPLOYING 状态不允许手动操作
        if (currentEnum == DeployStatusEnum.DEPLOYING) {
            throw new BusinessException(ErrorCode.DEPLOY_STATUS_ERROR, 
                    "应用正在部署中，请稍后操作");
        }

        throw new BusinessException(ErrorCode.DEPLOY_STATUS_ERROR, 
                "未知的部署状态：" + currentStatus);
    }

    /**
     * 归档 Vue 项目（混合策略：源码 + dist 缓存）
     */
    private String archiveVueProject(String deployKey, Long appId) {
        if (StrUtil.isBlank(deployKey)) {
            return null;
        }

        String sourceDirName = "vue_project_" + appId;
        String sourcePath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        String archivePath = AppConstant.CODE_ARCHIVE_ROOT_DIR + File.separator 
                             + appId + "_" + deployKey + "_vue";

        try {
            File sourceDir = new File(sourcePath);
            ThrowUtils.throwIf(!sourceDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目源码不存在");

            // 创建归档目录
            File archiveDir = new File(archivePath);
            FileUtil.mkdir(archiveDir);

            // 1. 归档源码（排除 node_modules 和 dist）
            // 使用 copyContent 而非 copy，避免外层目录被重复嵌套
            File sourceArchiveDir = new File(archivePath + "/source");
            FileUtil.mkdir(sourceArchiveDir);
            FileUtil.copyContent(sourceDir, sourceArchiveDir, true);
            // 复制后删除不需要的目录
            FileUtil.del(new File(sourceArchiveDir, "node_modules"));
            FileUtil.del(new File(sourceArchiveDir, "dist"));

            // 2. 归档 dist（如果存在，用于快速恢复）
            File distDir = new File(sourcePath, "dist");
            if (distDir.exists()) {
                File distArchiveDir = new File(archivePath + "/dist");
                FileUtil.del(distArchiveDir);
                FileUtil.mkdir(distArchiveDir);
                FileUtil.copyContent(distDir, distArchiveDir, true);
                log.info("Vue 项目 dist 目录已缓存");
            }

            log.info("Vue 项目已归档（混合策略）：{}", archivePath);
            return archivePath;

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目归档失败：" + e.getMessage());
        }
    }

    /**
     * 归档 HTML/Multi-file 项目
     */
    private String archiveDeployDirectory(String deployKey, Long appId) {
        if (StrUtil.isBlank(deployKey)) {
            return null;
        }

        String sourcePath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        String archivePath = AppConstant.CODE_ARCHIVE_ROOT_DIR + File.separator 
                              + appId + "_" + deployKey;

        try {
            File sourceDir = new File(sourcePath);
            ThrowUtils.throwIf(!sourceDir.exists(), ErrorCode.SYSTEM_ERROR, "部署目录不存在");
            // 移动目录到归档
            FileUtil.move(sourceDir, new File(archivePath), true);
            log.info("应用部署目录已归档：{}", archivePath);
            return archivePath;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "归档失败：" + e.getMessage());
        }
    }

    /**
     * 恢复并重新构建 Vue 项目（缓存优先策略）
     */
    private void restoreAndBuildVueProject(String archivePath, String deployKey, Long appId) {
        if (StrUtil.isBlank(archivePath)) {
            throw new BusinessException(ErrorCode.DEPLOY_STATUS_ERROR, "归档目录不存在，无法恢复上线");
        }

        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;

        try {
            // 策略 1：优先使用缓存的 dist（7天内有效）
            File cachedDist = new File(archivePath + "/dist");
            if (cachedDist.exists()) {
                log.info("使用缓存的 dist 目录快速恢复 Vue 项目");
                // 先清理目标目录，确保扁平展开
                FileUtil.del(new File(deployDirPath));
                FileUtil.copyContent(cachedDist, new File(deployDirPath), true);
                return;
            }

            // 策略 2：从归档恢复源码 → 调用 buildProject() 重新构建
            log.info("缓存 dist 不存在或已过期，从归档恢复并重新构建 Vue 项目");
            String sourcePath = archivePath + "/source";
            String restorePath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator 
                                 + "vue_project_" + appId;

            // 1. 恢复源码到输出目录（先清理再复制，避免旧文件残留和 copy 嵌套问题）
            FileUtil.del(new File(restorePath));
            FileUtil.copyContent(new File(sourcePath), new File(restorePath), true);
            log.info("Vue 项目源码已恢复：{} → {}", sourcePath, restorePath);

            // 2. 【关键】复用 VueProjectBuilder.buildProject() 完成 npm install + build
            boolean buildSuccess = vueProjectBuilder.buildProject(restorePath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目重新构建失败");

            // 3. 复制 dist 到部署目录
            File distDir = new File(restorePath, "dist");
            // 先清理目标目录，确保扁平展开
            FileUtil.del(new File(deployDirPath));
            FileUtil.copyContent(distDir, new File(deployDirPath), true);
            log.info("Vue 项目部署成功：{} → {}", distDir.getAbsolutePath(), deployDirPath);

        } catch (Exception e) {
            // 失败时清理部署目录
            FileUtil.del(new File(deployDirPath));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目恢复失败：" + e.getMessage());
        }
    }

    /**
     * 恢复 HTML/Multi-file 项目部署目录
     */
    private void restoreDeployDirectory(String archivePath, String deployKey) {
        String targetPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;

        try {
            File archiveDir = new File(archivePath);
            ThrowUtils.throwIf(!archiveDir.exists(), ErrorCode.SYSTEM_ERROR, "归档目录不存在");

            // 清理目标目录（防止 Hutool FileUtil.copy 在目录已存在时嵌套复制）
            FileUtil.del(new File(targetPath));
            // 复制内容到部署目录（保留归档，DB 更新成功后由调用方清理）
            FileUtil.copyContent(archiveDir, new File(targetPath), true);
            log.info("部署目录已恢复：{} → {}", archivePath, targetPath);

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "恢复失败：" + e.getMessage());
        }
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
