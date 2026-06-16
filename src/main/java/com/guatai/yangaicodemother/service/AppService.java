package com.guatai.yangaicodemother.service;

import com.guatai.yangaicodemother.model.dto.app.AppAddRequest;
import com.guatai.yangaicodemother.model.dto.app.AppQueryRequest;
import com.guatai.yangaicodemother.model.dto.app.ChatToGenCodeRequest;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.vo.AppVO;
import com.guatai.yangaicodemother.model.vo.DeployStatusVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.guatai.yangaicodemother.model.entity.App;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 应用 服务层。
 *
 */
public interface AppService extends IService<App> {
    /**
     * 创建应用
     * @param appAddRequest 应用添加请求
     * @param loginUser 登录用户
     * @return 应用id
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 根据应用获取 AppVO
     *
     * @param app 应用
     * @return AppVO
     */
    AppVO getAppVO(App app);

    /**
     * 根据应用列表获取 AppVO 列表
     *
     * @param appList 应用列表
     * @return AppVO 列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 根据管理员查询条件获取查询包装器
     *
     * @param appQueryRequest 查询条件
     * @return 查询包装器
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 根据应用 id 和消息生成代码（流式 SSE）
     * <p>
     * 图片统一由前端在对话前通过独立接口上传并保存到 app_image 表，
     * 后端通过 {@code enrichWithImageContext} 自动从 DB 拉取图片信息注入提示词。
     *
     * @param request   聊天请求 DTO（含 appId、message、ragEnabled、skillNames）
     * @param loginUser 登录用户（由 Controller 从 Session 获取）
     * @return 代码生成流
     */
    Flux<String> chatToGenCode(ChatToGenCodeRequest request, User loginUser);

    /**
     * 部署应用
     *
     * @param appId 应用id
     * @param loginUser 登录用户
     * @return
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 内部部署（精选应用自动部署/重新部署，无用户鉴权）
     * <p>
     * 用于审核通过后自动部署、内容审核通过后重新部署等场景。
     * 不校验用户权限，DEPLOYING 状态静默跳过，代码目录不存在时静默跳过。
     *
     * @param appId         应用 ID
     * @param triggerSource 触发来源标识（如 "review-auto-deploy"、"content-review-approve"）
     * @return 部署访问 URL，跳过时返回 null
     */
    String internalDeployApp(Long appId, String triggerSource);
    /**
     * 异步生成应用截图并更新封面
     *
     * <p>使用虚拟线程执行器执行截图任务，不阻塞调用方。
     * 返回的 {@link CompletableFuture} 可用于追踪任务执行状态。</p>
     *
     * @param appId  应用id
     * @param appUrl 应用url
     * @return 异步任务句柄，可用于追踪截图是否完成
     */
    CompletableFuture<Void> generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 应用下线（取消部署）
     *
     * @param appId 应用id
     * @param loginUser 登录用户
     * @return 操作结果信息
     */
    String deployOffline(Long appId, User loginUser);

    /**
     * 应用上线（恢复部署）
     *
     * @param appId 应用id
     * @param loginUser 登录用户
     * @return 部署访问 URL
     */
    String deployOnline(Long appId, User loginUser);

    /**
     * 查询应用部署状态
     *
     * @param appId 应用id
     * @return 部署状态信息
     */
    DeployStatusVO getDeployStatus(Long appId);

    /**
     * 根据 deployKey 查询应用（用于静态资源访问拦截）
     *
     * @param deployKey 部署标识
     * @return 应用信息
     */
    App getByDeployKey(String deployKey);
}
