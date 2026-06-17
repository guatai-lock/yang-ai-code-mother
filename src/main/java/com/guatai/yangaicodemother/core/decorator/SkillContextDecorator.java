package com.guatai.yangaicodemother.core.decorator;

import cn.hutool.core.collection.CollUtil;
import com.guatai.yangaicodemother.config.SkillsLoader;
import com.guatai.yangaicodemother.model.entity.SkillMeta;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 技能上下文装饰器 — 将已启用的设计规范/最佳实践技能内容注入用户消息头部
 * <p>
 * 执行优先级次于图片装饰器（{@code @Order(20)}），确保技能内容在图片资源之后注入。
 */
@Slf4j
@Component
@Order(20)
public class SkillContextDecorator implements MessageDecorator {

    @Resource
    private SkillsLoader skillsLoader;

    @Override
    public boolean isEnabled(DecorateContext context) {
        // 仅在传入 skillNames 时启用
        return CollUtil.isNotEmpty(context.skillNames());
    }

    @Override
    public String decorate(String message, DecorateContext context) {
        List<String> skillNames = context.skillNames();
        if (CollUtil.isEmpty(skillNames)) {
            return message;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("【已启用的设计规范与最佳实践】\n");
            sb.append("请严格遵守以下规范生成代码：\n\n");
            for (String skillName : skillNames) {
                SkillMeta skill = skillsLoader.getSkillByName(skillName);
                if (skill != null) {
                    sb.append("=== ").append(skill.getName()).append(" ===\n");
                    sb.append(skill.getContent()).append("\n\n");
                } else {
                    log.warn("技能不存在，跳过: {}", skillName);
                }
            }
            sb.append("用户需求：").append(message);
            return sb.toString();
        } catch (Exception e) {
            log.warn("技能上下文注入失败，跳过: {}", e.getMessage());
            return message;
        }
    }
}
