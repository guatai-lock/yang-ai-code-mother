package com.guatai.yangaicodemother.nacos;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 线程安全的敏感词规则持有者
 * <p>
 * 使用 {@link AtomicReference} 包装不可变规则快照 {@link ImmutableRuleSet}，
 * 实现无锁并发读取。Nacos 配置变更时原子替换整个规则集。
 * </p>
 */
@Slf4j
@Component
public class SensitiveWordRules {

    private final AtomicReference<ImmutableRuleSet> ruleSetRef;

    public SensitiveWordRules() {
        this.ruleSetRef = new AtomicReference<>(ImmutableRuleSet.EMPTY);
    }

    /**
     * 原子更新规则集，由 {@link NacosConfigManager} 在配置变更时调用
     */
    public void update(ImmutableRuleSet newRules) {
        if (newRules == null) {
            log.warn("尝试更新 null 规则集，已忽略");
            return;
        }
        ImmutableRuleSet old = ruleSetRef.get();
        ruleSetRef.set(newRules);
        log.info("敏感词规则已更新: {} 个敏感词, {} 个注入模式, {} 个幻觉触发词, 启用={}",
                newRules.getSensitiveWords().size(),
                newRules.getInjectionPatterns().size(),
                newRules.getHallucinationTriggers().size(),
                newRules.isEnabled());
    }

    /**
     * 获取当前规则快照（无锁，被守卫线程高频调用）
     */
    public ImmutableRuleSet current() {
        return ruleSetRef.get();
    }

    /**
     * 不可变规则快照
     * <p>
     * 所有字段在构造时通过 {@link List#copyOf} 创建防御性副本，
     * 确保后续任何外部修改不影响已发布的快照。
     * </p>
     */
    @Getter
    public static class ImmutableRuleSet {

        /** 空规则集（启动时默认值） */
        public static final ImmutableRuleSet EMPTY = new ImmutableRuleSet(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                1000,
                true
        );

        private final List<String> sensitiveWords;
        private final List<Pattern> injectionPatterns;
        private final List<String> hallucinationTriggers;
        private final Pattern hallucinationPattern;
        private final int maxInputLength;
        private final boolean enabled;

        public ImmutableRuleSet(
                List<String> sensitiveWords,
                List<Pattern> injectionPatterns,
                List<String> hallucinationTriggers,
                Pattern hallucinationPattern,
                int maxInputLength,
                boolean enabled) {
            this.sensitiveWords = List.copyOf(sensitiveWords);
            this.injectionPatterns = List.copyOf(injectionPatterns);
            this.hallucinationTriggers = List.copyOf(hallucinationTriggers);
            this.hallucinationPattern = hallucinationPattern;
            this.maxInputLength = maxInputLength;
            this.enabled = enabled;
        }
    }
}
