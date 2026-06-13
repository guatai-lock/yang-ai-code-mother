package com.guatai.yangaicodemother.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 应用部署成功事件
 *
 * <p>当应用部署成功时发布，
 * {@code RagCorpusUpdateListener} 监听此事件后更新精选应用的 embedding。</p>
 */
@Getter
public class AppDeployedEvent extends ApplicationEvent {

    private final Long appId;
    private final String deployKey;
    private final String codeGenType;

    public AppDeployedEvent(Object source, Long appId, String deployKey, String codeGenType) {
        super(source);
        this.appId = appId;
        this.deployKey = deployKey;
        this.codeGenType = codeGenType;
    }
}
