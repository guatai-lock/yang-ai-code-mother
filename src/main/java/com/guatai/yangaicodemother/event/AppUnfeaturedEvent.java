package com.guatai.yangaicodemother.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 应用取消精选事件
 *
 * <p>当应用被管理员取消精选时发布，
 * {@code RagCorpusUpdateListener} 监听此事件后从向量库移除该应用的代码。</p>
 */
@Getter
public class AppUnfeaturedEvent extends ApplicationEvent {

    private final Long appId;

    public AppUnfeaturedEvent(Object source, Long appId) {
        super(source);
        this.appId = appId;
    }
}
