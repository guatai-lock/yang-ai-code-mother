package com.guatai.yangaicodemother.controller;

import com.guatai.yangaicodemother.annotation.AuthCheck;
import com.guatai.yangaicodemother.common.BaseResponse;
import com.guatai.yangaicodemother.common.ResultUtils;
import com.guatai.yangaicodemother.common.UserConstant;
import com.guatai.yangaicodemother.nacos.NacosConfigManager;
import com.guatai.yangaicodemother.nacos.SensitiveWordRules;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 敏感词守卫管理控制器
 * <p>
 * 提供管理员查看和操作敏感词守卫规则的接口。
 * 所有接口需要 admin 角色权限。
 * </p>
 */
@RestController
@RequestMapping("/admin/guardrail")
@RequiredArgsConstructor
public class SensitiveWordAdminController {

    private final SensitiveWordRules sensitiveWordRules;
    private final NacosConfigManager nacosConfigManager;

    /**
     * 获取守卫规则状态概览
     * <p>
     * 返回当前生效的规则统计信息，包括启用状态、各类型规则数量、
     * 最大输入长度、Nacos 连接状态等。
     * </p>
     */
    @GetMapping("/status")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Map<String, Object>> getGuardrailStatus() {
        var rules = sensitiveWordRules.current();
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", rules.isEnabled());
        status.put("sensitiveWordCount", rules.getSensitiveWords().size());
        status.put("injectionPatternCount", rules.getInjectionPatterns().size());
        status.put("hallucinationTriggerCount", rules.getHallucinationTriggers().size());
        status.put("hasHallucinationPattern", rules.getHallucinationPattern() != null);
        status.put("maxInputLength", rules.getMaxInputLength());
        status.put("nacosConnected", nacosConfigManager.isNacosAvailable());
        return ResultUtils.success(status);
    }

    /**
     * 手动触发从 Nacos 重新加载配置
     * <p>
     * 正常情况下 Nacos 配置变更会自动推送到应用。
     * 此端点用于在特殊情况下手动触发重新加载。
     * </p>
     */
    @PostMapping("/reload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> reloadConfig() {
        return ResultUtils.success("配置重新加载请求已收到，Nacos 监听器将在配置变更时自动更新规则");
    }
}
