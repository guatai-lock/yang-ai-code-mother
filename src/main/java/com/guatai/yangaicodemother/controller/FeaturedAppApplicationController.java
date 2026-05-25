package com.guatai.yangaicodemother.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.annotation.AuthCheck;
import com.guatai.yangaicodemother.common.BaseResponse;
import com.guatai.yangaicodemother.common.ResultUtils;
import com.guatai.yangaicodemother.common.UserConstant;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.exception.ThrowUtils;
import com.guatai.yangaicodemother.model.dto.featuredapp.*;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.vo.FeaturedAppApplicationVO;
import com.guatai.yangaicodemother.service.FeaturedAppApplicationService;
import com.guatai.yangaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 精选申请 控制层
 */
@RestController
@RequestMapping("/featuredApp")
@Slf4j
public class FeaturedAppApplicationController {

    @Resource
    private FeaturedAppApplicationService featuredAppApplicationService;

    @Resource
    private UserService userService;

    /**
     * 用户申请精选
     *
     * @param request 申请请求
     * @param httpRequest HTTP请求
     * @return 申请记录id
     */
    @PostMapping("/apply")
    public BaseResponse<Long> applyFeaturedApp(@RequestBody FeaturedAppApplyRequest request, 
                                                HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        // 参数校验
        ThrowUtils.throwIf(request.getAppId() == null, ErrorCode.PARAMS_ERROR, "应用id不能为空");
        ThrowUtils.throwIf(StrUtil.length(request.getReason()) > 1024, ErrorCode.PARAMS_ERROR, "申请理由不能超过1024个字符");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(httpRequest);
        // 执行申请
        Long applicationId = featuredAppApplicationService.applyFeaturedApp(
            request.getAppId(), 
            request.getReason(), 
            loginUser
        );
        return ResultUtils.success(applicationId);
    }

    /**
     * 更新申请状态 (撤销等)
     *
     * @param request 更新请求
     * @param httpRequest HTTP请求
     * @return 是否成功
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApplication(@RequestBody FeaturedAppUpdateRequest request,
                                                    HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        // 参数校验
        ThrowUtils.throwIf(request.getApplicationId() == null, ErrorCode.PARAMS_ERROR, "申请记录id不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(request.getAction()), ErrorCode.PARAMS_ERROR, "操作类型不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(httpRequest);
        // 执行更新
        Boolean result = featuredAppApplicationService.updateApplication(
            request.getApplicationId(), 
            request.getAction(), 
            loginUser
        );
        return ResultUtils.success(result);
    }

    /**
     * 查询我的申请列表
     *
     * @param request 查询请求
     * @param httpRequest HTTP请求
     * @return 申请列表分页
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<FeaturedAppApplicationVO>> listMyApplications(@RequestBody FeaturedAppQueryRequest request,
                                                                            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(httpRequest);
        // 强制设置userId为当前登录用户,防止越权查询
        request.setUserId(loginUser.getId());
        // 分页参数校验
        long pageSize = request.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 条申请记录");
        ThrowUtils.throwIf(request.getPageNum() > 100, ErrorCode.PARAMS_ERROR, "页码不能超过100");
        // 执行查询
        Page<FeaturedAppApplicationVO> result = featuredAppApplicationService.listMyApplications(request);
        return ResultUtils.success(result);
    }

    /**
     * 管理员审核申请 (单个或批量)
     *
     * @param request 审核请求
     * @param httpRequest HTTP请求
     * @return 成功处理的数量
     */
    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> reviewApplications(@RequestBody FeaturedAppReviewRequest request,
                                                     HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        // 参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(request.getApplicationIds()), ErrorCode.PARAMS_ERROR, "申请记录id列表不能为空");
        ThrowUtils.throwIf(request.getApproved() == null, ErrorCode.PARAMS_ERROR, "审核结果不能为空");
        // 获取当前登录管理员
        User adminUser = userService.getLoginUser(httpRequest);
        // 执行审核
        Integer successCount = featuredAppApplicationService.reviewApplications(
            request.getApplicationIds(),
            request.getApproved(),
            request.getReviewComment(),
            adminUser
        );
        return ResultUtils.success(successCount);
    }

    /**
     * 管理员查询申请列表
     *
     * @param request 查询请求
     * @return 申请列表分页
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<FeaturedAppApplicationVO>> listApplicationsByAdmin(@RequestBody FeaturedAppQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        // 分页参数校验
        long pageSize = request.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 条申请记录");
        ThrowUtils.throwIf(request.getPageNum() > 100, ErrorCode.PARAMS_ERROR, "页码不能超过100");
        // 执行查询
        Page<FeaturedAppApplicationVO> result = featuredAppApplicationService.listApplicationsByAdmin(request);
        return ResultUtils.success(result);
    }
}
