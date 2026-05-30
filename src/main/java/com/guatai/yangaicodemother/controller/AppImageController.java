package com.guatai.yangaicodemother.controller;

import com.guatai.yangaicodemother.common.BaseResponse;
import com.guatai.yangaicodemother.common.ResultUtils;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.exception.ThrowUtils;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.vo.AppImageVO;
import com.guatai.yangaicodemother.service.AppImageService;
import com.guatai.yangaicodemother.service.AppService;
import com.guatai.yangaicodemother.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 应用图片资源 控制层。
 */
@RestController
@RequestMapping("/app/image")
@Slf4j
public class AppImageController {

    @Resource
    private AppImageService appImageService;

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    /**
     * 上传图片
     *
     * @param file        图片文件
     * @param appId       应用ID
     * @param description 图片描述（可选）
     * @param request     请求
     * @return 图片VO
     */
    @PostMapping("/upload")
    public BaseResponse<AppImageVO> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long appId,
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        // 1. 基础参数校验
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");

        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 3. 校验应用归属
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该应用");
        }

        // 4. 上传图片
        AppImageVO vo = appImageService.uploadImage(file, appId, description, loginUser);
        return ResultUtils.success(vo);
    }

    /**
     * 获取应用的图片列表
     *
     * @param appId   应用ID
     * @param request 请求
     * @return 图片VO列表
     */
    @GetMapping("/list/{appId}")
    public BaseResponse<List<AppImageVO>> listAppImages(
            @PathVariable Long appId,
            HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");

        // 获取当前登录用户并校验应用归属
        User loginUser = userService.getLoginUser(request);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看该应用图片");
        }

        List<AppImageVO> list = appImageService.getAppImagesByAppId(appId);
        return ResultUtils.success(list);
    }
}
