package com.guatai.yangaicodemother.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nacos 配置中心连接属性
 * 绑定 application.yml 中 nacos.config 前缀的配置
 */
@Component
@ConfigurationProperties(prefix = "nacos.config")
@Data
public class NacosConfigProperties {

    /** Nacos 服务器地址（必须配置，无默认值） */
    private String serverAddr;

    /** Nacos 命名空间 */
    private String namespace = "public";

    /** 敏感词规则配置的 Data ID（必须配置，无默认值） */
    private String dataId;

    /** 配置分组（必须配置，无默认值） */
    private String group;

    /** Nacos 登录用户名（Nacos 1.2.0+ 需认证） */
    private String username = "";

    /** Nacos 登录密码 */
    private String password = "";

    /** 连接/读取超时时间（毫秒） */
    private long timeoutMs = 3000;

    /** Nacos 不可用时的降级默认值 */
    private Fallback fallback = new Fallback();

    @Data
    public static class Fallback {

        /** 守卫是否启用 */
        private boolean enabled = true;

        /** 最大输入长度 */
        private int maxInputLength = 1000;

        /** 敏感词列表 */
        private List<String> sensitiveWords = new ArrayList<>(List.of(
                "忽略之前的指令", "ignore previous", "ignore above",
                "破解", "hack", "绕过", "bypass", "越狱", "jailbreak",
                "忘记之前", "你现在是", "假装你是", "系统提示"
        ));

        /** 注入攻击模式（正则表达式） */
        private List<String> injectionPatterns = new ArrayList<>(List.of(
                "(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)",
                "(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)",
                "(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)",
                "(?i)system\\s*:\\s*you\\s+are",
                "(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:"
        ));

        /** 会诱发模型幻觉的指令 */
        private List<String> hallucinationTriggers = new ArrayList<>(List.of(
                "随便写", "编一个", "虚构", "假代码", "示例就行",
                "不用真实", "随便生成", "不用可用", "伪代码",
                "随便写写", "不用运行", "不用实现", "大概写"
        ));

        /** 代码生成幻觉模式（正则表达式） */
        private String hallucinationPattern = "(?i)(随便|编造|虚构|假的|示例|不用运行|不用真实)";

        /** 提示词重写功能开关（默认关闭，需在 Nacos 中显式开启） */
        private boolean rewritingEnabled = false;

        /** 敏感词→安全替代词映射 */
        private Map<String, String> sensitiveWordReplacements = new HashMap<>(Map.of(
                "破解", "开发",
                "hack", "build",
                "绕过", "通过",
                "bypass", "access via",
                "越狱", "突破限制",
                "jailbreak", "unlock"
        ));

        /** 幻觉触发词→专业替代词映射 */
        private Map<String, String> hallucinationTriggerReplacements = new HashMap<>(Map.ofEntries(
                Map.entry("随便写", "请生成"),
                Map.entry("编一个", "请编写"),
                Map.entry("虚构", "请设计"),
                Map.entry("假代码", "示例代码"),
                Map.entry("示例就行", "请提供完整示例"),
                Map.entry("不用真实", "请使用示例数据"),
                Map.entry("随便生成", "请生成"),
                Map.entry("不用可用", "请确保可用"),
                Map.entry("伪代码", "实际代码"),
                Map.entry("随便写写", "请认真编写"),
                Map.entry("不用运行", "请确保可运行"),
                Map.entry("不用实现", "请完整实现"),
                Map.entry("大概写", "请准确实现")
        ));
    }
}
