package com.guatai.yangaicodemother.controller;

import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.common.BaseResponse;
import com.guatai.yangaicodemother.common.ResultUtils;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.exception.ThrowUtils;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.enums.DeployStatusEnum;
import com.guatai.yangaicodemother.model.vo.ChatHistoryPublicVO;
import com.guatai.yangaicodemother.ratelimit.annotation.RateLimit;
import com.guatai.yangaicodemother.ratelimit.enums.RateLimitType;
import com.guatai.yangaicodemother.service.AppService;
import com.guatai.yangaicodemother.service.ChatHistoryService;
import com.guatai.yangaicodemother.service.ProjectDownloadService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.LocalDateTime;

/**
 * 精选应用公共访问控制器
 * <p>
 * 提供精选应用的对话历史查看和源代码下载功能，无需登录即可访问。
 * 所有端点均进行联合校验：isFeaturedApp() + 可选 publicChatHistory 检查。
 */
@Slf4j
@RestController
@RequestMapping("/app/good")
public class FeaturedAppController {

    @Resource
    private AppService appService;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ProjectDownloadService projectDownloadService;

    /**
     * 公开查询精选应用的对话历史
     * <p>
     * 需同时满足：精选状态（priority=99, deployStatus=ONLINE, deployKey NOT NULL）
     * 且应用设置了 publicChatHistory=true。
     *
     * @param appId          应用ID
     * @param pageSize       每页条数（默认10，最大50）
     * @param lastCreateTime 游标：上次最后一条记录的创建时间
     * @return 对话历史分页（脱敏，不含 userId）
     */
    @GetMapping("/{appId}/chatHistory")
    @RateLimit(limitType = RateLimitType.IP, rate = 20, rateInterval = 60,
            message = "请求过于频繁，请稍后再试")
    public BaseResponse<Page<ChatHistoryPublicVO>> listPublicChatHistory(
            @PathVariable Long appId,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) LocalDateTime lastCreateTime) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        // 2. 查询应用 + 联合校验
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!canViewPublicChatHistory(app)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "该应用未公开对话过程");
        }
        // 3. 查询对话历史
        Page<ChatHistoryPublicVO> result = chatHistoryService.listPublicChatHistory(appId, pageSize, lastCreateTime);
        return ResultUtils.success(result);
    }

    /**
     * 下载精选应用的源代码
     * <p>
     * 仅需精选状态校验（不检查 publicChatHistory），所有精选应用均可下载。
     *
     * @param appId    应用ID
     * @param response HTTP 响应
     */
    @GetMapping("/{appId}/download")
    @RateLimit(limitType = RateLimitType.IP, rate = 3, rateInterval = 60,
            message = "下载过于频繁，请稍后再试")
    public void downloadAppCode(@PathVariable Long appId,
                                HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        // 2. 查询应用 + 精选状态校验（下载对所有精选应用开放）
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!isFeaturedApp(app)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "该应用不是精选应用");
        }
        // 3. 构建应用代码目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 4. 检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在");
        // 5. 生成下载文件名
        String downloadFileName = "featured_app_" + appId;
        // 6. 调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }

    /**
     * 判断应用是否为精选应用
     */
    private boolean isFeaturedApp(App app) {
        return AppConstant.GOOD_APP_PRIORITY.equals(app.getPriority())
                && DeployStatusEnum.ONLINE.getValue().equals(app.getDeployStatus())
                && StrUtil.isNotBlank(app.getDeployKey());
    }

    /**
     * 判断是否可公开查看对话历史
     * <p>
     * 联合校验：精选状态 + publicChatHistory == true
     */
    private boolean canViewPublicChatHistory(App app) {
        return isFeaturedApp(app) && Boolean.TRUE.equals(app.getPublicChatHistory());
    }
}
