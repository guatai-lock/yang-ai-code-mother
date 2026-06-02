package com.guatai.yangaicodemother.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.guatai.yangaicodemother.config.NacosConfigProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos 配置中心连接管理器
 * <p>
 * 负责初始化 Nacos ConfigService 连接，订阅敏感词规则配置变更，
 * 并在 Nacos 不可用时优雅降级到 application.yml 中的 fallback 默认值。
 * </p>
 *
 * <h3>降级策略</h3>
 * <ul>
 *   <li>启动时 Nacos 不可达 → 使用 fallback 默认值，打印 WARN 日志</li>
 *   <li>运行时 Nacos 断连 → 最后已知规则继续生效，监听器静默等待重连</li>
 *   <li>Nacos 恢复 → 监听器自动收到推送，热更新规则集</li>
 *   <li>JSON 解析失败 → 保留旧规则，日志记录错误</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NacosConfigManager {

    private final NacosConfigProperties properties;
    private final SensitiveWordRules sensitiveWordRules;
    private final RuleSetParser ruleSetParser;

    private ConfigService configService;
    private final AtomicBoolean nacosAvailable = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        try {
            // 构建 Nacos 连接属性
            java.util.Properties nacosProps = new java.util.Properties();
            nacosProps.put("serverAddr", properties.getServerAddr());
            nacosProps.put("namespace", properties.getNamespace());
            // Nacos 2.0+ 需要认证（如果配置了用户名密码）
            String username = properties.getUsername();
            String password = properties.getPassword();
            if (!username.isEmpty() && !password.isEmpty()) {
                nacosProps.put("username", username);
                nacosProps.put("password", password);
            }

            // 创建 ConfigService
            this.configService = NacosFactory.createConfigService(nacosProps);
            log.info("Nacos ConfigService 已初始化, 服务器: {}", properties.getServerAddr());

            // 同步获取初始配置
            String configJson = null;
            try {
                configJson = configService.getConfig(
                        properties.getDataId(),
                        properties.getGroup(),
                        properties.getTimeoutMs()
                );
            } catch (NacosException e) {
                log.warn("Nacos 获取配置失败: {} (将使用 fallback 默认值)", e.getMessage());
            }

            if (configJson != null && !configJson.isEmpty()) {
                applyConfig(configJson);
                nacosAvailable.set(true);
                log.info("已从 Nacos 加载初始敏感词规则");
            } else {
                log.warn("Nacos 配置 [dataId={}, group={}] 为空或不存在，使用 fallback 默认值",
                        properties.getDataId(), properties.getGroup());
                applyFallbackConfig();
            }

            // 注册配置变更监听器（异步）
            configService.addListener(properties.getDataId(), properties.getGroup(), new Listener() {
                @Override
                public Executor getExecutor() {
                    return Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "nacos-config-listener");
                        t.setDaemon(true);
                        return t;
                    });
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("Nacos 配置已变更，正在应用更新...");
                    applyConfig(configInfo);
                    nacosAvailable.set(true);
                }
            });

            log.info("已订阅 Nacos 配置: dataId={}, group={}", properties.getDataId(), properties.getGroup());

        } catch (Exception e) {
            log.error("初始化 Nacos ConfigService 失败，将使用 fallback 默认值: {}", e.getMessage());
            applyFallbackConfig();
        }
    }

    /**
     * 应用从 Nacos 获取的 JSON 配置
     */
    private void applyConfig(String json) {
        if (json == null || json.isEmpty()) {
            log.warn("Nacos 配置内容为空，跳过更新");
            return;
        }
        SensitiveWordRules.ImmutableRuleSet rules = ruleSetParser.parseFromJson(json);
        if (rules != null) {
            sensitiveWordRules.update(rules);
        }
    }

    /**
     * 使用 application.yml 中的 fallback 默认值
     */
    private void applyFallbackConfig() {
        NacosConfigProperties.Fallback fb = properties.getFallback();
        SensitiveWordRules.ImmutableRuleSet rules = ruleSetParser.buildFromFallback(
                fb.getSensitiveWords(),
                fb.getInjectionPatterns(),
                fb.getHallucinationTriggers(),
                fb.getHallucinationPattern(),
                fb.getMaxInputLength(),
                fb.isEnabled()
        );
        sensitiveWordRules.update(rules);
    }

    /**
     * Nacos 连接是否可用
     */
    public boolean isNacosAvailable() {
        return nacosAvailable.get();
    }

    @PreDestroy
    public void destroy() {
        if (configService != null) {
            try {
                configService.shutDown();
                log.info("Nacos ConfigService 已关闭");
            } catch (NacosException e) {
                log.warn("关闭 Nacos ConfigService 时发生异常: {}", e.getMessage());
            }
        }
    }
}
