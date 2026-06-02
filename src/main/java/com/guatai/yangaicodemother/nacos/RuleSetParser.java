package com.guatai.yangaicodemother.nacos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guatai.yangaicodemother.nacos.SensitiveWordRules.ImmutableRuleSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 敏感词规则配置解析器
 * <p>
 * 负责将 Nacos JSON 配置或 application.yml fallback 属性解析为 {@link ImmutableRuleSet}。
 * Pattern 在解析时预先编译，确保守卫线程无需执行编译操作。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleSetParser {

    private final ObjectMapper objectMapper;

    /**
     * 从 Nacos JSON 字符串解析规则集
     * <p>
     * 预期 JSON 结构（新增 rewriteRules 节，可选）：
     * <pre>
     * {
     *   "enabled": true,
     *   "maxInputLength": 1000,
     *   "sensitiveWords": ["忽略之前的指令", ...],
     *   "injectionPatterns": ["(?i)ignore\\s+...", ...],
     *   "hallucinationTriggers": ["随便写", ...],
     *   "hallucinationPattern": "(?i)(随便|编造|...)",
     *   "rewriteRules": {
     *     "enabled": true,
     *     "sensitiveWordReplacements": {"破解": "开发", ...},
     *     "hallucinationTriggerReplacements": {"随便写": "请生成", ...}
     *   }
     * }
     * </pre>
     */
    public ImmutableRuleSet parseFromJson(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<String> sensitiveWords = (List<String>) map.getOrDefault("sensitiveWords", List.of());
            @SuppressWarnings("unchecked")
            List<String> injectionPatternStrings = (List<String>) map.getOrDefault("injectionPatterns", List.of());
            @SuppressWarnings("unchecked")
            List<String> hallucinationTriggers = (List<String>) map.getOrDefault("hallucinationTriggers", List.of());
            String hallucinationPatternStr = (String) map.getOrDefault("hallucinationPattern", "");
            int maxInputLength = map.containsKey("maxInputLength")
                    ? ((Number) map.get("maxInputLength")).intValue() : 1000;
            boolean enabled = map.containsKey("enabled")
                    ? Boolean.parseBoolean(map.get("enabled").toString()) : true;

            // 解析重写规则（可选，兼容旧配置）
            @SuppressWarnings("unchecked")
            Map<String, Object> rewriteRules = (Map<String, Object>) map.getOrDefault("rewriteRules", Map.of());
            boolean rewritingEnabled = rewriteRules.containsKey("enabled")
                    ? Boolean.parseBoolean(rewriteRules.get("enabled").toString()) : false;
            @SuppressWarnings("unchecked")
            Map<String, String> sensitiveWordReplacements = rewriteRules.containsKey("sensitiveWordReplacements")
                    ? (Map<String, String>) rewriteRules.get("sensitiveWordReplacements")
                    : Map.of();
            @SuppressWarnings("unchecked")
            Map<String, String> hallucinationTriggerReplacements = rewriteRules.containsKey("hallucinationTriggerReplacements")
                    ? (Map<String, String>) rewriteRules.get("hallucinationTriggerReplacements")
                    : Map.of();

            // 预先编译正则表达式（线程安全，仅在此处执行一次）
            List<Pattern> injectionPatterns = injectionPatternStrings.stream()
                    .map(Pattern::compile)
                    .collect(Collectors.toList());
            Pattern hallucinationPattern = hallucinationPatternStr.isEmpty()
                    ? null : Pattern.compile(hallucinationPatternStr);

            log.debug("从 Nacos JSON 解析到 {} 个敏感词, {} 个注入模式, {} 个幻觉触发词, {} 个重写替换",
                    sensitiveWords.size(), injectionPatterns.size(), hallucinationTriggers.size(),
                    sensitiveWordReplacements.size());

            return new ImmutableRuleSet(
                    sensitiveWords, injectionPatterns, hallucinationTriggers,
                    hallucinationPattern, maxInputLength, enabled,
                    sensitiveWordReplacements, hallucinationTriggerReplacements, rewritingEnabled);
        } catch (Exception e) {
            log.error("解析 Nacos 配置 JSON 失败，将保留当前规则: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 fallback 属性构建规则集（Nacos 不可用时使用）
     */
    public ImmutableRuleSet buildFromFallback(
            List<String> sensitiveWords,
            List<String> injectionPatternStrings,
            List<String> hallucinationTriggers,
            String hallucinationPatternStr,
            int maxInputLength,
            boolean enabled,
            Map<String, String> sensitiveWordReplacements,
            Map<String, String> hallucinationTriggerReplacements,
            boolean rewritingEnabled) {

        List<Pattern> injectionPatterns = injectionPatternStrings.stream()
                .map(Pattern::compile)
                .collect(Collectors.toList());
        Pattern hallucinationPattern = (hallucinationPatternStr == null || hallucinationPatternStr.isEmpty())
                ? null : Pattern.compile(hallucinationPatternStr);

        log.info("使用 fallback 默认规则: {} 个敏感词, {} 个注入模式, {} 个幻觉触发词, {} 个重写替换",
                sensitiveWords.size(), injectionPatterns.size(), hallucinationTriggers.size(),
                sensitiveWordReplacements.size());

        return new ImmutableRuleSet(
                sensitiveWords, injectionPatterns, hallucinationTriggers,
                hallucinationPattern, maxInputLength, enabled,
                sensitiveWordReplacements, hallucinationTriggerReplacements, rewritingEnabled);
    }
}
