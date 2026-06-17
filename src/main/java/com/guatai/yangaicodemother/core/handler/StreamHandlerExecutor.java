package com.guatai.yangaicodemother.core.handler;

import com.guatai.yangaicodemother.core.strategy.CodeGenStrategyRegistry;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import com.guatai.yangaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 流处理器执行器
 * <p>
 * 通过 {@link CodeGenStrategyRegistry} 委派给各生成类型的策略获取对应的 {@link StreamHandler}，
 * 不再硬编码 switch 分发。新增生成类型只需添加策略实现，无需修改此类。
 */
@Slf4j
@Component
public class StreamHandlerExecutor {

    @Resource
    private CodeGenStrategyRegistry strategyRegistry;

    /**
     * 创建流处理器并处理聊天历史记录
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @param codeGenType        代码生成类型
     * @return 处理后的流
     */
    public Flux<String> doExecute(Flux<String> originFlux,
                                  ChatHistoryService chatHistoryService,
                                  long appId, User loginUser, CodeGenTypeEnum codeGenType) {
        return strategyRegistry.getStrategy(codeGenType)
                .createStreamHandler()
                .handle(originFlux, chatHistoryService, appId, loginUser);
    }
}
