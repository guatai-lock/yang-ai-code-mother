package com.guatai.yangaicodemother.service;

import com.guatai.yangaicodemother.model.dto.app.AppAddRequest;
import com.guatai.yangaicodemother.model.dto.app.AppQueryRequest;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.guatai.yangaicodemother.model.entity.App;
import reactor.core.publisher.Flux;

import java.util.List;

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
     * 生成应用截图
     *
     * @param appId 应用id
     * @param appUrl 应用url
     */
    void generateAppScreenshotAsync(Long appId, String appUrl);
}
