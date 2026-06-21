package com.guatai.yangaicodemother.core.pipeline.stage;

import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.core.pipeline.GenStage;
import com.guatai.yangaicodemother.core.pipeline.PipelineContext;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.model.enums.DeployStatusEnum;
import com.guatai.yangaicodemother.service.FeaturedAppApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.SignalType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 精选应用内容重新审核 Stage
 * <p>
 * 仅在 cleanup 阶段执行——在代码生成成功后（{@link SignalType#ON_COMPLETE}），
 * 如果当前应用是精选且已部署，则异步提交内容重新审核。
 * </p>
 * <p>
 * execute 阶段快照应用状态，判断是否需要后续触发审核；
 * cleanup 阶段根据信号类型和状态快照决定是否执行审核。
 * </p>
 *
 * @see FeaturedAppApplicationService#requestContentReview(Long, com.guatai.yangaicodemother.model.entity.User)
 */
@Slf4j
@Component
@Order(60)
public class ContentReviewStage implements GenStage {

    private final FeaturedAppApplicationService featuredAppApplicationService;

    private final ExecutorService virtualThreadExecutor;

    public ContentReviewStage(
            @Lazy FeaturedAppApplicationService featuredAppApplicationService,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.featuredAppApplicationService = featuredAppApplicationService;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Override
    public void execute(PipelineContext context) {
        // 快照：判断当前应用是否为"精选已部署"状态
        // 此快照在 execute 阶段捕获，确保反映 Flux 创建前的应用状态
        boolean isFeaturedDeployed = AppConstant.GOOD_APP_PRIORITY.equals(context.getApp().getPriority())
                && DeployStatusEnum.ONLINE.getValue().equals(context.getApp().getDeployStatus());
        context.setFeaturedDeployedApp(isFeaturedDeployed);
        log.debug("ContentReviewStage 快照完成: appId={}, featuredDeployed={}",
                context.getAppId(), isFeaturedDeployed);
    }

    @Override
    public void cleanup(PipelineContext context, SignalType signalType) {
        // 仅在 ON_COMPLETE 时触发，ON_ERROR / ON_CANCEL 不创建虚假的审核申请
        if (signalType != SignalType.ON_COMPLETE || !context.isFeaturedDeployedApp()) {
            return;
        }

        Long appId = context.getAppId();
        log.info("精选应用代码生成完成，异步提交内容重新审核: appId={}", appId);

        CompletableFuture.runAsync(() -> {
            try {
                featuredAppApplicationService.requestContentReview(appId, context.getLoginUser());
            } catch (BusinessException e) {
                // 已有待审核申请等预期内的异常，只记日志
                log.info("精选应用提交内容审核跳过: appId={}, reason={}", appId, e.getMessage());
            } catch (Exception e) {
                log.error("精选应用提交内容审核失败: appId={}", appId, e);
            }
        }, virtualThreadExecutor);
    }
}
