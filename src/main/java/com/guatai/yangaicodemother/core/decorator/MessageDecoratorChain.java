package com.guatai.yangaicodemother.core.decorator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 消息装饰器链 — 有序执行所有已注册的 {@link MessageDecorator}
 * <p>
 * Spring 自动收集所有 {@code @Component} 装饰器 Bean，并按 {@link org.springframework.core.annotation.Order @Order}
 * 排序后依次执行。新增装饰器只需实现接口并注册为 Bean，零修改此链。
 * <p>
 * 设计类似 Phase 1 的 {@link com.guatai.yangaicodemother.core.strategy.CodeGenStrategyRegistry}，
 * 通过构造注入自动发现装饰器，无需手动注册。
 *
 * @see MessageDecorator
 * @see ImageContextDecorator
 * @see SkillContextDecorator
 */
@Slf4j
@Component
public class MessageDecoratorChain {

    private final List<MessageDecorator> decorators;

    /**
     * 构造注入自动收集所有 {@link MessageDecorator} Bean，
     * 按 {@code @Order} 排序后存入不可变列表
     */
    public MessageDecoratorChain(List<MessageDecorator> decorators) {
        List<MessageDecorator> sorted = new ArrayList<>(decorators);
        AnnotationAwareOrderComparator.sort(sorted);
        this.decorators = Collections.unmodifiableList(sorted);
        log.info("消息装饰器链初始化完成，已注册装饰器: {}",
                this.decorators.stream().map(d -> d.getClass().getSimpleName()).toList());
    }

    /**
     * 对消息依次执行所有已启用的装饰器
     *
     * @param message 原始用户消息
     * @param context 装饰上下文
     * @return 经过所有装饰器链式处理后的消息
     */
    public String decorate(String message, DecorateContext context) {
        String result = message;
        for (MessageDecorator decorator : decorators) {
            if (decorator.isEnabled(context)) {
                log.debug("执行装饰器: {}", decorator.getClass().getSimpleName());
                result = decorator.decorate(result, context);
            } else {
                log.debug("跳过装饰器: {}（未启用）", decorator.getClass().getSimpleName());
            }
        }
        return result;
    }

    /**
     * 获取已注册的装饰器列表（只读视图）
     */
    public List<MessageDecorator> getDecorators() {
        return decorators;
    }
}
