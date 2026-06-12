package com.guatai.yangaicodemother.service;

import com.guatai.yangaicodemother.model.dto.app.AppAddRequest;
import com.guatai.yangaicodemother.model.dto.app.AppQueryRequest;
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
     * 根据应用id和消息生成代码
     *
     * @param appId 应用id
     * @param message 消息
     * @param loginUser 登录用户
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 部署应用
     *
     * @param appId 应用id
     * @param loginUser 登录用户
     * @return
     */
    String deployApp(Long appId, User loginUser);
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
