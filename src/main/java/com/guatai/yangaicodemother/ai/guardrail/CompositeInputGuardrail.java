package com.guatai.yangaicodemother.ai.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 综合输入护轨
 * 整合了提示词安全、输入有效性、代码生成防幻觉等功能
 */
public class CompositeInputGuardrail implements InputGuardrail {

    // ========== 提示词安全相关 ==========
    
    // 敏感词列表
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令", "ignore previous", "ignore above",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak",
            "忘记之前", "你现在是", "假装你是", "系统提示"
    );

    // 注入攻击模式
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    // ========== 代码生成防幻觉相关 ==========
    
    // 会诱发模型幻觉的指令 → 直接拦截
    private static final List<String> HALLUCINATION_TRIGGERS = List.of(
            "随便写", "编一个", "虚构", "假代码", "示例就行",
            "不用真实", "随便生成", "不用可用", "伪代码",
            "随便写写", "不用运行", "不用实现", "大概写"
    );

    private static final Pattern HALLUCINATION_PATTERN = Pattern.compile(
            "(?i)(随便|编造|虚构|假的|示例|不用运行|不用真实)"
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();
        
        // 1. 检查是否为空
        if (input.trim().isEmpty()) {
            return fatal("输入内容不能为空");
        }
        
        // 2. 检查输入有效性（防止纯特殊字符）
        if (input.trim().matches("[^a-zA-Z0-9\\u4e00-\\u9fa5]+")) {
            return fatal("输入内容无效，请输入有效描述");
        }
        
        // 3. 检查输入长度
        if (input.length() > 1000) {
            return fatal("输入内容过长，不要超过 1000 字");
        }
        
        // 4. 检查敏感词（提示词安全）
        String lowerInput = input.toLowerCase();
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerInput.contains(sensitiveWord.toLowerCase())) {
                return fatal("输入包含不当内容，请修改后重试");
            }
        }
        
        // 5. 检查注入攻击模式（提示词安全）
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return fatal("检测到恶意输入，请求被拒绝");
            }
        }
        
        // 6. 检查代码生成幻觉指令
        for (String trigger : HALLUCINATION_TRIGGERS) {
            if (lowerInput.contains(trigger)) {
                return fatal("非法指令：禁止生成虚构/不可运行/伪代码，必须生成真实可执行代码！");
            }
        }
        
        // 7. 检查代码生成幻觉模式
        if (HALLUCINATION_PATTERN.matcher(input).find()) {
            return fatal("检测到代码生成幻觉指令，已拦截！");
        }
        
        return success();
    }
}
