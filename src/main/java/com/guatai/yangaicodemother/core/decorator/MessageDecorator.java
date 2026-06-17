package com.guatai.yangaicodemother.core.decorator;

/**
 * 消息装饰器 — 对用户输入消息进行富化/增强
 * <p>
 * 实现 {@link org.springframework.core.annotation.Order @Order} 以控制执行顺序。
 * 新增装饰器只需：
 * <ol>
 *   <li>实现此接口并注册为 Spring {@link org.springframework.stereotype.Component @Component}</li>
 *   <li>用 {@code @Order} 标注执行优先级</li>
 *   <li>（可选）重写 {@link #isEnabled(DecorateContext)} 按条件开关</li>
 * </ol>
 * 无需修改 {@link com.guatai.yangaicodemother.core.decorator.MessageDecoratorChain} 或
 * {@link com.guatai.yangaicodemother.core.AiCodeGeneratorFacade}。
 *
 * @see ImageContextDecorator
 * @see SkillContextDecorator
 * @see MessageDecoratorChain
 */
@FunctionalInterface
public interface MessageDecorator {

    /**
     * 装饰消息
     *
     * @param message 原始或已由前序装饰器处理过的消息
     * @param context 装饰上下文（appId、skillNames 等）
     * @return 装饰后的消息
     */
    String decorate(String message, DecorateContext context);

    /**
     * 是否启用此装饰器
     * <p>
     * 默认始终启用，子类可重写以按条件开关（如检查配置项或上下文参数）。
     *
     * @param context 装饰上下文
     * @return true 表示执行此装饰器
     */
    default boolean isEnabled(DecorateContext context) {
        return true;
    }
}
