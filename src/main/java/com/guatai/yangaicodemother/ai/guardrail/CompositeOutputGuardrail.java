package com.guatai.yangaicodemother.ai.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 综合输出护轨
 * 整合了代码生成输出验证、幻觉检测、完整性检查等功能
 */
public class CompositeOutputGuardrail implements OutputGuardrail {

    // 幻觉关键词（出现即拦截）
    private static final List<String> HALLUCINATION_KEYWORDS = List.of(
            "虚构", "假设", "大概", "可能", "也许", "我猜测", "不确定",
            "伪代码", "示例代码", "测试代码", "假数据", "不存在",
            "未实现", "待补充", "后续完善", "省略", "此处省略",
            "无法生成", "不能运行", "不可用", "仅作示例", "示意"
    );

    // 非法代码模式（正则）
    private static final List<Pattern> INVALID_CODE_PATTERNS = List.of(
            Pattern.compile("TODO", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\.{3,}"), // 多个点 ...
            Pattern.compile("class.*\\{\\s*\\}"), // 空类
            Pattern.compile("public.*\\(\\)\\s*\\{\\s*\\}"), // 空方法
            Pattern.compile("interface.*\\{\\s*\\}"),
            Pattern.compile("//.*(生成|编写|实现).*代码")
    );

    @Override
    public OutputGuardrailResult validate(AiMessage aiMessage) {
        String content = aiMessage.text();

        // 1. 输出为空 → 拦截
        if (content == null || content.isBlank()) {
            return fatal("模型输出内容为空，可能产生幻觉");
        }

        String lowerContent = content.toLowerCase();

        // 2. 拦截幻觉关键词
        for (String keyword : HALLUCINATION_KEYWORDS) {
            if (lowerContent.contains(keyword.toLowerCase())) {
                return fatal("检测到模型幻觉内容：" + keyword + "，已拦截");
            }
        }

        // 3. 拦截非法/不完整代码模式
        for (Pattern pattern : INVALID_CODE_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return fatal("检测到不完整/伪代码/幻觉实现，已拦截");
            }
        }

        // 4. 幻觉工具调用拦截（增强）
        if (aiMessage.toolExecutionRequests() != null) {
            for (var toolRequest : aiMessage.toolExecutionRequests()) {
                String toolName = toolRequest.name();
                if (isHallucinationTool(toolName)) {
                    return fatal("模型幻觉调用不存在的工具：" + toolName + "，已拦截");
                }
            }
        }

        return success();
    }

    /**
     * 判断是否为幻觉工具（可结合白名单）
     */
    private boolean isHallucinationTool(String toolName) {
        List<String> ALLOWED_TOOLS = List.of(
                "generateCode", "generateHtml", "generateSql", 
                "generateApi", "searchProject", "getFile"
        );
        return !ALLOWED_TOOLS.contains(toolName);
    }
}
