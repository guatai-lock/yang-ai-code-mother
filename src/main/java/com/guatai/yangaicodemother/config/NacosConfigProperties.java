package com.guatai.yangaicodemother.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
    }
}
