package com.guatai.yangaicodemother.core.pipeline.stage;

import com.guatai.yangaicodemother.core.pipeline.GenStage;
import com.guatai.yangaicodemother.core.pipeline.PipelineContext;
import com.guatai.yangaicodemother.mapper.AppMapper;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.guatai.yangaicodemother.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 对话历史持久化 Stage
 * <p>
 * 将用户消息保存到数据库，并递增应用的对话轮次。
 * </p>
 * <p>
 * <b>注</b>：使用 {@link AppMapper#update(App)} 而非 {@code AppService.updateById()}
 * 来避免与 {@code AppServiceImpl} 的循环依赖。
 * </p>
 */
@Slf4j
@Component
@Order(30)
public class ChatHistoryStage implements GenStage {

    private final ChatHistoryService chatHistoryService;

    private final AppMapper appMapper;

    public ChatHistoryStage(@Lazy ChatHistoryService chatHistoryService, AppMapper appMapper) {
        this.chatHistoryService = chatHistoryService;
        this.appMapper = appMapper;
    }

    @Override
    public void execute(PipelineContext context) {
        Long appId = context.getAppId();

        // 1. 保存用户消息到对话历史
        chatHistoryService.addChatMessage(
                appId,
                context.getOriginalMessage(),
                ChatHistoryMessageTypeEnum.USER.getValue(),
                context.getLoginUser().getId()
        );

        // 2. 递增应用对话轮次
        App app = context.getApp();
        App roundUpdate = new App();
        roundUpdate.setId(appId);
        roundUpdate.setConversationRound(
                app.getConversationRound() == null ? 1 : app.getConversationRound() + 1
        );
        appMapper.update(roundUpdate);

        log.debug("对话历史已保存, appId={}, round={}",
                appId, roundUpdate.getConversationRound());
    }
}
