package com.guatai.yangaicodemother.ai.guardrail;

import com.guatai.yangaicodemother.nacos.SensitiveWordRules;
import com.guatai.yangaicodemother.nacos.SensitiveWordRules.ImmutableRuleSet;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 综合输入护轨
 * <p>
 * 整合了提示词安全、输入有效性、代码生成防幻觉等功能。
 * 规则集由 {@link SensitiveWordRules} 动态提供，支持通过 Nacos 配置中心热更新。
 * </p>
 *
 * <h3>检查流程</h3>
 * <ol>
 *   <li>空输入检查</li>
 *   <li>纯特殊字符检查</li>
 *   <li>最大长度检查（动态配置）</li>
 *   <li>敏感词检查（动态列表）</li>
 *   <li>注入攻击模式检查（动态正则）</li>
 *   <li>代码生成幻觉指令检查（动态列表）</li>
 *   <li>代码生成幻觉模式检查（动态正则）</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeInputGuardrail implements InputGuardrail {

    private final SensitiveWordRules sensitiveWordRules;

    /**
     * 守卫名称，用于日志标识
     */
    private static final String GUARDRAIL_NAME = "CompositeInputGuardrail";

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();
        ImmutableRuleSet rules = sensitiveWordRules.current();

        // 快速路径：守卫被禁用时跳过所有检查
        if (!rules.isEnabled()) {
            log.debug("[{}] 守卫已被禁用，跳过检查", GUARDRAIL_NAME);
            return success();
        }

        // 1. 检查是否为空
        if (input.trim().isEmpty()) {
            log.debug("[{}] 空输入被拦截", GUARDRAIL_NAME);
            return fatal("输入内容不能为空");
        }

        // 2. 检查输入有效性（防止纯特殊字符）
        if (input.trim().matches("[^a-zA-Z0-9\\u4e00-\\u9fa5]+")) {
            log.debug("[{}] 纯特殊字符输入被拦截", GUARDRAIL_NAME);
            return fatal("输入内容无效，请输入有效描述");
        }

        // 3. 检查输入长度（动态配置）
        if (input.length() > rules.getMaxInputLength()) {
            log.debug("[{}] 输入超长被拦截: {} > {}", GUARDRAIL_NAME, input.length(), rules.getMaxInputLength());
            return fatal("输入内容过长，不要超过 " + rules.getMaxInputLength() + " 字");
        }

        // 4. 检查敏感词（提示词安全）
        String lowerInput = input.toLowerCase();
        for (String sensitiveWord : rules.getSensitiveWords()) {
            if (lowerInput.contains(sensitiveWord.toLowerCase())) {
                log.debug("[{}] 敏感词拦截: [{}]", GUARDRAIL_NAME, sensitiveWord);
                return fatal("输入包含不当内容，请修改后重试");
            }
        }

        // 5. 检查注入攻击模式（提示词安全）
        for (Pattern pattern : rules.getInjectionPatterns()) {
            if (pattern.matcher(input).find()) {
                log.debug("[{}] 注入模式拦截: [{}]", GUARDRAIL_NAME, pattern.pattern());
                return fatal("检测到恶意输入，请求被拒绝");
            }
        }

        // 6. 检查代码生成幻觉指令
        for (String trigger : rules.getHallucinationTriggers()) {
            if (lowerInput.contains(trigger)) {
                log.debug("[{}] 幻觉触发词拦截: [{}]", GUARDRAIL_NAME, trigger);
                return fatal("非法指令：禁止生成虚构/不可运行/伪代码，必须生成真实可执行代码！");
            }
        }

        // 7. 检查代码生成幻觉模式
        if (rules.getHallucinationPattern() != null
                && rules.getHallucinationPattern().matcher(input).find()) {
            log.debug("[{}] 幻觉模式拦截: [{}]", GUARDRAIL_NAME, rules.getHallucinationPattern().pattern());
            return fatal("检测到代码生成幻觉指令，已拦截！");
        }

        return success();
    }
}
