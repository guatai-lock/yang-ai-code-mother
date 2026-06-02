package com.guatai.yangaicodemother.ai.guardrail;

import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.nacos.SensitiveWordRules;
import com.guatai.yangaicodemother.nacos.SensitiveWordRules.ImmutableRuleSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 提示词重写服务（互轨机制 — 外轨）
 * <p>
 * 在 {@link CompositeInputGuardrail} 之前执行，当检测到潜在风险内容时，
 * 主动重写提示词（移除/替换敏感信息、优化专业表达），而不是直接拒绝请求。
 * </p>
 *
 * <h3>双轨保护架构</h3>
 * <ul>
 *   <li><b>外轨 (Rail 1):</b> PromptRewriteService — 主动修复，将不安全/不规范的提示词改写为安全版本</li>
 *   <li><b>内轨 (Rail 2):</b> CompositeInputGuardrail — 安全兜底，重写后的提示词仍会经过完整的守卫检查</li>
 * </ul>
 *
 * <h3>重写流程</h3>
 * <ol>
 *   <li>空/空白输入检查 → 原样返回</li>
 *   <li>重写功能禁用检查 → 原样返回</li>
 *   <li>风险快检：是否包含任何需要重写的内容 → 无风险则原样返回（零干扰）</li>
 *   <li>敏感词替换：按 {@code sensitiveWordReplacements} 逐条替换（按词长度降序，避免部分替换）</li>
 *   <li>注入模式移除：匹配 {@code injectionPatterns}，移除注入片段，保留用户实际需求</li>
 *   <li>幻觉触发词替换：按 {@code hallucinationTriggerReplacements} 替换为专业表述</li>
 *   <li>记录重写日志</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptRewriteService {

    private final SensitiveWordRules sensitiveWordRules;

    /**
     * 服务名称，用于日志标识
     */
    private static final String REWRITER_NAME = "PromptRewriter";

    /**
     * 重写用户提示词
     * <p>
     * 检测潜在风险并自动重写，返回安全的提示词版本。
     * 如果无需重写，返回原始文本（零干扰）。
     * </p>
     *
     * @param originalPrompt 用户原始提示词
     * @return 重写后的安全提示词，或原始提示词（无需重写时）
     */
    public String rewrite(String originalPrompt) {
        // 1. 空/空白输入 → 原样返回
        if (StrUtil.isBlank(originalPrompt)) {
            return originalPrompt;
        }

        ImmutableRuleSet rules = sensitiveWordRules.current();

        // 2. 重写功能未启用 → 原样返回
        if (!rules.isRewritingEnabled()) {
            return originalPrompt;
        }

        // 3. 风险快检：是否有任何需要重写的内容
        if (!requiresRewrite(originalPrompt, rules)) {
            return originalPrompt;
        }

        // 执行重写
        String result = originalPrompt;

        // 4. 敏感词替换
        result = replaceSensitiveWords(result, rules);

        // 5. 注入模式移除
        result = removeInjectionPatterns(result, rules);

        // 6. 幻觉触发词替换
        result = replaceHallucinationTriggers(result, rules);

        // 7. 记录重写日志
        log.info("[{}] 提示词已重写: [{}] → [{}]", REWRITER_NAME, originalPrompt, result);

        return result;
    }

    /**
     * 快速检查：提示词是否包含任何需要重写的内容
     * <p>
     * 零干扰设计：对完全正常的提示词不做任何处理。
     * </p>
     */
    private boolean requiresRewrite(String input, ImmutableRuleSet rules) {
        if (!rules.hasRewriteRules()) {
            return false;
        }

        String lowerInput = input.toLowerCase();

        // 检查是否需要敏感词替换
        for (Map.Entry<String, String> entry : rules.getSensitiveWordReplacements().entrySet()) {
            if (containsIgnoreCase(lowerInput, entry.getKey())) {
                return true;
            }
        }

        // 检查是否需要幻觉触发词替换
        for (Map.Entry<String, String> entry : rules.getHallucinationTriggerReplacements().entrySet()) {
            if (containsIgnoreCase(lowerInput, entry.getKey())) {
                return true;
            }
        }

        // 检查是否需要注入模式移除
        for (Pattern pattern : rules.getInjectionPatterns()) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 敏感词替换
     * <p>
     * 按关键词长度降序替换（优先匹配长词），避免短关键词被部分替换。
     * 例如：优先替换"忽略之前的指令"而不是先替换"忽略"。
     * </p>
     */
    private String replaceSensitiveWords(String input, ImmutableRuleSet rules) {
        if (rules.getSensitiveWordReplacements().isEmpty()) {
            return input;
        }

        String result = input;

        // 按关键词长度降序排序，避免部分替换
        List<Map.Entry<String, String>> sortedEntries = rules.getSensitiveWordReplacements().entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .collect(Collectors.toList());

        for (Map.Entry<String, String> entry : sortedEntries) {
            String target = entry.getKey();
            String replacement = entry.getValue();

            // 大小写不敏感的替换
            result = replaceIgnoreCase(result, target, replacement);
        }

        return result;
    }

    /**
     * 注入模式移除
     * <p>
     * 匹配注入正则表达式，将匹配到的注入片段从提示词中移除，
     * 同时保留用户的真实需求。
     * </p>
     */
    private String removeInjectionPatterns(String input, ImmutableRuleSet rules) {
        if (rules.getInjectionPatterns().isEmpty()) {
            return input;
        }

        String result = input;
        for (Pattern pattern : rules.getInjectionPatterns()) {
            Matcher matcher = pattern.matcher(result);
            if (matcher.find()) {
                // 移除匹配的注入部分
                result = matcher.replaceAll("").trim();
                log.debug("[{}] 注入模式已移除: [{}]", REWRITER_NAME, pattern.pattern());
            }
        }

        return result;
    }

    /**
     * 幻觉触发词替换
     * <p>
     * 将可能引发模型幻觉的触发词替换为专业、建设性的表述。
     * 例如："随便写" → "请生成"，"不用实现" → "请完整实现"。
     * </p>
     */
    private String replaceHallucinationTriggers(String input, ImmutableRuleSet rules) {
        if (rules.getHallucinationTriggerReplacements().isEmpty()) {
            return input;
        }

        String result = input;

        // 按关键词长度降序排序
        List<Map.Entry<String, String>> sortedEntries = rules.getHallucinationTriggerReplacements().entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .collect(Collectors.toList());

        for (Map.Entry<String, String> entry : sortedEntries) {
            String target = entry.getKey();
            String replacement = entry.getValue();
            result = replaceIgnoreCase(result, target, replacement);
        }

        return result;
    }

    /**
     * 大小写不敏感的字符串包含检测
     */
    private boolean containsIgnoreCase(String lowerInput, String target) {
        return lowerInput.contains(target.toLowerCase());
    }

    /**
     * 大小写不敏感的字符串替换
     * <p>
     * 使用正则表达式实现，支持大小写不敏感替换。
     * </p>
     */
    private String replaceIgnoreCase(String input, String target, String replacement) {
        if (StrUtil.isEmpty(target)) {
            return input;
        }
        return input.replaceAll("(?i)" + Pattern.quote(target), Matcher.quoteReplacement(replacement));
    }
}
