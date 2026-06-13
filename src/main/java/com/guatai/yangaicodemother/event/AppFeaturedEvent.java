package com.guatai.yangaicodemother.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 应用被精选事件
 *
 * <p>当应用被管理员审核通过（精选）时发布，
 * {@code RagCorpusUpdateListener} 监听此事件后增量加载该应用的代码到向量库。</p>
 */
@Getter
public class AppFeaturedEvent extends ApplicationEvent {

    private final Long appId;

    public AppFeaturedEvent(Object source, Long appId) {
        super(source);
        this.appId = appId;
    }
}
