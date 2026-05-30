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
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.core.AiCodeGeneratorFacade;
import com.guatai.yangaicodemother.core.builder.VueProjectBuilder;
import com.guatai.yangaicodemother.core.handler.StreamHandlerExecutor;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.exception.ThrowUtils;
import com.guatai.yangaicodemother.event.AppDeletedEvent;
import com.guatai.yangaicodemother.model.dto.app.AppAddRequest;
import com.guatai.yangaicodemother.model.dto.app.AppQueryRequest;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import com.guatai.yangaicodemother.model.enums.DeployStatusEnum;
import com.guatai.yangaicodemother.model.vo.AppVO;
import com.guatai.yangaicodemother.model.vo.DeployStatusVO;
import com.guatai.yangaicodemother.model.vo.UserVO;
import com.guatai.yangaicodemother.monitor.MonitorContext;
import com.guatai.yangaicodemother.monitor.MonitorContextHolder;
import com.guatai.yangaicodemother.service.ChatHistoryService;
import com.guatai.yangaicodemother.service.ScreenshotService;
import com.guatai.yangaicodemother.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.mapper.AppMapper;
import com.guatai.yangaicodemother.service.AppService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        // 使用 AI 智能选择代码生成类型(多例模式)
        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = routingService.routeCodeGenType(initPrompt);
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
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
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
        // 5. 通过校验后，添加用户消息到对话历(保存到数据库)
        chatHistoryService.addChatMessage(appId, message,
                ChatHistoryMessageTypeEnum.USER.getValue(),
                loginUser.getId());
        // 5.5 递增应用对话轮次
        App roundUpdate = new App();
        roundUpdate.setId(appId);
        roundUpdate.setConversationRound(app.getConversationRound() == null ? 1 : app.getConversationRound() + 1);
        this.updateById(roundUpdate);
        //6设置监控上下文
        MonitorContextHolder.setContext(
                MonitorContext.builder()
                        .appId(appId.toString())
                        .userId(loginUser.getId().toString())
                        .build()
        );
    // 7. 调用 AI 生成代码（流式）
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
    // 8. 收集 AI 响应内容并在完成后记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService,
                appId, loginUser, codeGenTypeEnum)
                .doFinally(
                        // 清理监控上下文(无论成功/失败/取消)
                        signalType -> MonitorContextHolder.clearContext()
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

            // 5. 检查是否已有 deployKey
            String deployKey = app.getDeployKey();
            if (StrUtil.isBlank(deployKey)) {
                deployKey = RandomUtil.randomString(6);
            }

            // 6. 获取代码生成类型，构建源目录路径
            String codeGenType = app.getCodeGenType();
            String sourceDirName = codeGenType + "_" + appId;
            String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;

            // 7. 检查源目录是否存在
            File sourceDir = new File(sourceDirPath);
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
            }

            // 8. Vue 项目特殊处理：执行构建
            CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
            if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
                boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
                ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");

                File distDir = new File(sourceDirPath, "dist");
                ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
                sourceDir = distDir;
                log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
            }

            // 9. 复制文件到部署目录
            String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
            try {
                FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
            }

            // 10. 更新应用的 deployKey 和部署时间
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

            // 11. 返回可访问的 URL
            String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
            generateAppScreenshotAsync(appId, appDeployUrl);
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
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        Thread.startVirtualThread(()  ->{
            try {
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
                if (StrUtil.isBlank(screenshotUrl)) {
                    log.warn("应用 {} 截图生成结果为空，跳过封面更新", appId);
                    return;
                }
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(screenshotUrl);
                boolean updated = this.updateById(updateApp);
                ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
            } catch (Exception e) {
                log.error("应用 {} 截图生成失败: {}", appId, e.getMessage());
            }
        });
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

            // 6. 先保存文件操作所需的旧值（DB 更新后 deployKey 会被清空）
            String oldDeployKey = app.getDeployKey();
            CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());

            // 7. 更新数据库（先于文件操作，保证状态一致）
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setDeployStatus(DeployStatusEnum.OFFLINE.getValue());
            updateApp.setDeployKey(null);
            updateApp.setEditTime(LocalDateTime.now());
            boolean result = this.updateById(updateApp);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "应用下线失败");
            // ↑ DB 已提交，状态为 OFFLINE。
            //   即使后续文件操作失败，也比"DB 仍显示 ONLINE 但文件已丢失"更安全

            // 8. 文件归档（DB 已一致，文件操作失败不影响状态正确性）
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
