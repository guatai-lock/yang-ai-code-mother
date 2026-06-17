package com.guatai.yangaicodemother.core;

import cn.hutool.core.collection.CollUtil;
import com.guatai.yangaicodemother.ai.AiCodeGeneratorService;
import com.guatai.yangaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.guatai.yangaicodemother.config.SkillsLoader;
import com.guatai.yangaicodemother.core.strategy.CodeGenStrategy;
import com.guatai.yangaicodemother.core.strategy.CodeGenStrategyRegistry;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.model.entity.SkillMeta;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import com.guatai.yangaicodemother.model.vo.AppImageVO;
import com.guatai.yangaicodemother.service.AppImageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

/**
 * AI 代码生成外观类，组合生成和保存功能
 * <p>
 * 职责：
 * <ol>
 *   <li>消息富化（图片上下文、技能上下文）— 后续将抽取为独立装饰器链</li>
 *   <li>委派具体生成行为给 {@link CodeGenStrategy}（通过 {@link CodeGenStrategyRegistry}）</li>
 * </ol>
 * 新增代码生成类型时无需修改此类，只需添加 {@link CodeGenStrategy} 实现。
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    @Lazy
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private CodeGenStrategyRegistry strategyRegistry;

    @Resource
    private AppImageService appImageService;

    @Resource
    private SkillsLoader skillsLoader;

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @param skillNames      启用的技能名称列表（可选）
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum,
                                                   Long appId, List<String> skillNames) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 消息富化
        String enrichedMessage = enrichWithImageContext(userMessage, appId);
        enrichedMessage = enrichWithSkills(enrichedMessage, skillNames);
        // 获取 AI 服务 + 策略
        AiCodeGeneratorService codeGeneratorService = aiCodeGeneratorServiceFactory
                .getAiCodeGeneratorService(appId, codeGenTypeEnum);
        CodeGenStrategy strategy = strategyRegistry.getStrategy(codeGenTypeEnum);
        // 委派给策略执行流式生成
        return strategy.generateStream(codeGeneratorService, enrichedMessage, appId);
    }

    /**
     * 统一入口：根据类型生成并保存代码（非流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @param skillNames      启用的技能名称列表（可选）
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum,
                                     Long appId, List<String> skillNames) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 消息富化
        String enrichedMessage = enrichWithImageContext(userMessage, appId);
        enrichedMessage = enrichWithSkills(enrichedMessage, skillNames);
        // 获取 AI 服务 + 策略（非流式路径仅需默认服务配置）
        AiCodeGeneratorService codeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        CodeGenStrategy strategy = strategyRegistry.getStrategy(codeGenTypeEnum);
        // 委派给策略执行非流式生成 + 保存
        return strategy.generateAndSave(codeGeneratorService, enrichedMessage, appId);
    }

    // ======================== 消息富化（待 Phase 2 抽取为装饰器链） ========================

    /**
     * 富化用户消息：在用户消息前注入已上传的图片资源信息，供 AI 参考使用
     */
    private String enrichWithImageContext(String userMessage, Long appId) {
        try {
            List<AppImageVO> images = appImageService.getRecentImages(appId, 10);
            if (CollUtil.isEmpty(images)) {
                return userMessage;
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
            sb.append("用户需求：").append(userMessage);
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取上传图片信息失败，跳过图片上下文注入: {}", e.getMessage());
            return userMessage;
        }
    }

    /**
     * 富化用户消息：在用户消息前注入已启用的设计规范/最佳实践技能内容
     */
    private String enrichWithSkills(String userMessage, List<String> skillNames) {
        if (CollUtil.isEmpty(skillNames)) {
            return userMessage;
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
            sb.append("用户需求：").append(userMessage);
            return sb.toString();
        } catch (Exception e) {
            log.warn("技能上下文注入失败，跳过: {}", e.getMessage());
            return userMessage;
        }
    }
}
