package com.guatai.yangaicodemother.service;

import com.guatai.yangaicodemother.model.dto.featuredapp.FeaturedAppQueryRequest;
import com.guatai.yangaicodemother.model.entity.AppFeaturedApplication;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.vo.FeaturedAppApplicationVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 精选申请 服务层
 */
public interface FeaturedAppApplicationService extends IService<AppFeaturedApplication> {

    /**
     * 申请精选应用
     *
     * @param appId 应用id
     * @param reason 申请理由
     * @param loginUser 当前登录用户
     * @return 申请记录id
     */
    Long applyFeaturedApp(Long appId, String reason, User loginUser);

    /**
     * 更新申请状态 (支持撤销等操作)
     *
     * @param applicationId 申请记录id
     * @param action 操作类型：CANCEL(撤销)
     * @param loginUser 当前登录用户
     * @return 是否成功
     */
    Boolean updateApplication(Long applicationId, String action, User loginUser);

    /**
     * 审核申请 (支持单个和批量)
     *
     * @param applicationIds 申请记录id列表
     * @param approved 是否通过
     * @param reviewComment 审核意见
     * @param adminUser 管理员用户
     * @return 成功处理的数量
     */
    Integer reviewApplications(List<Long> applicationIds, Boolean approved,
                              String reviewComment, User adminUser);

    /**
     * 查询我的申请列表
     *
     * @param queryRequest 查询条件 (包含userId)
     * @return 申请列表分页
     */
    Page<FeaturedAppApplicationVO> listMyApplications(FeaturedAppQueryRequest queryRequest);

    /**
     * 管理员查询申请列表
     *
     * @param queryRequest 查询条件
     * @return 申请列表分页
     */
    Page<FeaturedAppApplicationVO> listApplicationsByAdmin(FeaturedAppQueryRequest queryRequest);

    /**
     * 检查应用是否已有待审核的申请
     *
     * @param appId 应用id
     * @return true=有待审核申请, false=无
     */
    boolean hasPendingApplication(Long appId);
}
