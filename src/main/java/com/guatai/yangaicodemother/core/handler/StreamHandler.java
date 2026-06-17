package com.guatai.yangaicodemother.core.handler;

import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.service.ChatHistoryService;
import reactor.core.publisher.Flux;

/**
 * 流处理器接口
 * <p>
 * 统一 HTML/MULTI_FILE（文本流）和 VUE_PROJECT（JSON 消息流）的流处理方式。
 * 每种 CodeGenType 通过其策略返回对应的处理器实例。
 */
public interface StreamHandler {

    /**
     * 处理原始流，附加对话历史记录等业务逻辑
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    Flux<String> handle(Flux<String> originFlux,
                        ChatHistoryService chatHistoryService,
                        long appId, User loginUser);
}
