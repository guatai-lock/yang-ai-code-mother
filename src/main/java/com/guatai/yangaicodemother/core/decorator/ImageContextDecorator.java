package com.guatai.yangaicodemother.core.decorator;

import cn.hutool.core.collection.CollUtil;
import com.guatai.yangaicodemother.model.vo.AppImageVO;
import com.guatai.yangaicodemother.service.AppImageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图片上下文装饰器 — 将已上传的图片资源信息注入用户消息头部
 * <p>
 * 使 AI 在生成代码时优先使用用户上传的图片（而非 picsum.photos 等占位服务）。
 * 执行优先级最高（{@code @Order(10)}），先于技能上下文注入。
 */
@Slf4j
@Component
@Order(10)
public class ImageContextDecorator implements MessageDecorator {

    @Resource
    private AppImageService appImageService;

    @Override
    public String decorate(String message, DecorateContext context) {
        Long appId = context.appId();
        if (appId == null) {
            return message;
        }
        try {
            List<AppImageVO> images = appImageService.getRecentImages(appId, 10);
            if (CollUtil.isEmpty(images)) {
                return message;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("【可用的图片资源】\n");
            sb.append("以下图片资源已上传到当前应用，请在生成的代码中优先使用这些图片（而不是 picsum.photos 等占位服务）：\n\n");
            for (int i = 0; i < images.size(); i++) {
                AppImageVO img = images.get(i);
                sb.append(i + 1).append(". ");
                if (img.getDescription() != null) {
                    sb.append(img.getDescription()).append(" - ");
                }
                sb.append(img.getOriginalName()).append("\n");
                sb.append("   URL: ").append(img.getCosUrl()).append("\n\n");
            }
            sb.append("用户需求：").append(message);
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取上传图片信息失败，跳过图片上下文注入: {}", e.getMessage());
            return message;
        }
    }
}
