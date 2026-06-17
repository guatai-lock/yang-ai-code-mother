package com.guatai.yangaicodemother.core.decorator;

import com.guatai.yangaicodemother.config.SkillsLoader;
import com.guatai.yangaicodemother.model.entity.SkillMeta;
import com.guatai.yangaicodemother.model.vo.AppImageVO;
import com.guatai.yangaicodemother.service.AppImageService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import jakarta.annotation.Resource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 2 消息装饰器链测试
 * <ol>
 *   <li>ImageContextDecorator 独立行为</li>
 *   <li>SkillContextDecorator 独立行为</li>
 *   <li>MessageDecoratorChain 链式执行与排序</li>
 *   <li>装饰器条件开关</li>
 * </ol>
 */
@SpringBootTest
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MessageDecoratorTest {

    private static final Long TEST_APP_ID = 999991L;
    private static final String TEST_MESSAGE = "帮我生成一个登录页面";

    // ==================== Mock 的外部依赖 ====================

    @MockBean
    private AppImageService appImageService;

    @MockBean
    private SkillsLoader skillsLoader;

    // ==================== 被测试的 Bean ====================

    @Resource
    private ImageContextDecorator imageContextDecorator;

    @Resource
    private SkillContextDecorator skillContextDecorator;

    @Resource
    private MessageDecoratorChain decoratorChain;

    // ==================== 第一部分：ImageContextDecorator ====================

    @Test
    @Order(1)
    void imageDecorator_withImages_shouldInjectImageContext() {
        // Arrange
        List<AppImageVO> images = List.of(
                createImageVO("logo.png", "公司 Logo", "https://cos.example.com/logo.png"),
                createImageVO("banner.jpg", "首页横幅", "https://cos.example.com/banner.jpg")
        );
        when(appImageService.getRecentImages(TEST_APP_ID, 10)).thenReturn(images);

        DecorateContext context = DecorateContext.builder().appId(TEST_APP_ID).build();

        // Act
        String result = imageContextDecorator.decorate(TEST_MESSAGE, context);

        // Assert
        assertTrue(result.contains("【可用的图片资源】"), "应包含图片资源标题");
        assertTrue(result.contains("logo.png"), "应包含图片文件名");
        assertTrue(result.contains("https://cos.example.com/logo.png"), "应包含图片 URL");
        assertTrue(result.contains("公司 Logo"), "应包含图片描述");
        assertTrue(result.contains(TEST_MESSAGE), "应保留原始用户消息");
        assertTrue(result.indexOf("【可用的图片资源】") < result.indexOf(TEST_MESSAGE),
                "图片上下文应注入在用户消息之前");
    }

    @Test
    @Order(2)
    void imageDecorator_withEmptyImages_shouldReturnOriginal() {
        // Arrange
        when(appImageService.getRecentImages(TEST_APP_ID, 10)).thenReturn(List.of());
        DecorateContext context = DecorateContext.builder().appId(TEST_APP_ID).build();

        // Act
        String result = imageContextDecorator.decorate(TEST_MESSAGE, context);

        // Assert
        assertEquals(TEST_MESSAGE, result, "无图片时应返回原始消息");
    }

    @Test
    @Order(3)
    void imageDecorator_withNullAppId_shouldReturnOriginal() {
        // Arrange
        DecorateContext context = DecorateContext.builder().appId(null).build();

        // Act
        String result = imageContextDecorator.decorate(TEST_MESSAGE, context);

        // Assert
        assertEquals(TEST_MESSAGE, result, "appId 为 null 时应返回原始消息");
        verify(appImageService, never()).getRecentImages(anyLong(), anyInt());
    }

    @Test
    @Order(4)
    void imageDecorator_whenServiceFails_shouldReturnOriginalGracefully() {
        // Arrange
        when(appImageService.getRecentImages(TEST_APP_ID, 10))
                .thenThrow(new RuntimeException("数据库连接失败"));
        DecorateContext context = DecorateContext.builder().appId(TEST_APP_ID).build();

        // Act
        String result = imageContextDecorator.decorate(TEST_MESSAGE, context);

        // Assert
        assertEquals(TEST_MESSAGE, result, "服务异常时应优雅降级返回原始消息");
    }

    @Test
    @Order(5)
    void imageDecorator_withImageNoDescription_shouldSkipDescription() {
        // Arrange
        AppImageVO img = new AppImageVO();
        img.setOriginalName("photo.jpg");
        img.setCosUrl("https://cos.example.com/photo.jpg");
        // description 为 null
        when(appImageService.getRecentImages(TEST_APP_ID, 10)).thenReturn(List.of(img));
        DecorateContext context = DecorateContext.builder().appId(TEST_APP_ID).build();

        // Act
        String result = imageContextDecorator.decorate(TEST_MESSAGE, context);

        // Assert
        assertTrue(result.contains("photo.jpg"), "应包含图片文件名");
        assertTrue(result.contains(TEST_MESSAGE), "应保留原始消息");
    }

    // ==================== 第二部分：SkillContextDecorator ====================

    @Test
    @Order(6)
    void skillDecorator_withSkills_shouldInjectSkillContext() {
        // Arrange
        SkillMeta designToken = new SkillMeta("design-tokens", "设计令牌", "--primary: #1890ff;");
        SkillMeta formValidation = new SkillMeta("form-validation", "表单验证", "必填项需校验");
        when(skillsLoader.getSkillByName("design-tokens")).thenReturn(designToken);
        when(skillsLoader.getSkillByName("form-validation")).thenReturn(formValidation);

        DecorateContext context = DecorateContext.builder()
                .skillNames(List.of("design-tokens", "form-validation"))
                .build();

        // Act
        String result = skillContextDecorator.decorate(TEST_MESSAGE, context);

        // Assert
        assertTrue(result.contains("【已启用的设计规范与最佳实践】"), "应包含技能标题");
        assertTrue(result.contains("design-tokens"), "应包含技能名称");
        assertTrue(result.contains("--primary: #1890ff;"), "应包含技能内容");
        assertTrue(result.contains(TEST_MESSAGE), "应保留原始用户消息");
    }

    @Test
    @Order(7)
    void skillDecorator_withEmptySkillNames_shouldReturnOriginal() {
        // Arrange
        DecorateContext context = DecorateContext.builder().skillNames(List.of()).build();

        // Act
        String result = skillContextDecorator.decorate(TEST_MESSAGE, context);

        // Assert
        assertEquals(TEST_MESSAGE, result, "无技能名称时应返回原始消息");
    }

    @Test
    @Order(8)
    void skillDecorator_withNullSkillNames_shouldReturnOriginal() {
        // Arrange
        DecorateContext context = DecorateContext.builder().skillNames(null).build();

        // Act
        String result = skillContextDecorator.decorate(TEST_MESSAGE, context);

        // Assert
        assertEquals(TEST_MESSAGE, result, "skillNames 为 null 时应返回原始消息");
    }

    @Test
    @Order(9)
    void skillDecorator_withUnknownSkill_shouldSkipGracefully() {
        // Arrange
        when(skillsLoader.getSkillByName("unknown-skill")).thenReturn(null);
        DecorateContext context = DecorateContext.builder()
                .skillNames(List.of("unknown-skill"))
                .build();

        // Act
        String result = skillContextDecorator.decorate(TEST_MESSAGE, context);

        // Assert
        assertTrue(result.contains(TEST_MESSAGE), "应保留原始消息");
        assertFalse(result.contains("unknown-skill"), "未知技能不应出现在结果中");
    }

    // ==================== 第三部分：isEnabled 开关测试 ====================

    @Test
    @Order(10)
    void skillDecorator_isEnabled_withEmptySkillNames_shouldReturnFalse() {
        // Arrange
        DecorateContext contextWithEmpty = DecorateContext.builder().skillNames(List.of()).build();
        DecorateContext contextWithNull = DecorateContext.builder().skillNames(null).build();
        DecorateContext contextWithSkills = DecorateContext.builder()
                .skillNames(List.of("design-tokens")).build();

        // Act & Assert
        assertFalse(skillContextDecorator.isEnabled(contextWithEmpty), "空列表应禁用");
        assertFalse(skillContextDecorator.isEnabled(contextWithNull), "null 应禁用");
        assertTrue(skillContextDecorator.isEnabled(contextWithSkills), "有技能名称应启用");
    }

    @Test
    @Order(11)
    void imageDecorator_isEnabled_shouldAlwaysReturnTrue() {
        // Arrange
        DecorateContext context = DecorateContext.builder().appId(1L).build();
        DecorateContext contextNull = DecorateContext.builder().build();

        // Act & Assert
        assertTrue(imageContextDecorator.isEnabled(context), "图片装饰器应始终启用");
        assertTrue(imageContextDecorator.isEnabled(contextNull), "即使缺少参数也应启用（由 decorate 内部处理）");
    }

    // ==================== 第四部分：MessageDecoratorChain 链式执行 ====================

    @Test
    @Order(12)
    void decoratorChain_shouldExecuteAllDecoratorsInOrder() {
        // Arrange
        when(appImageService.getRecentImages(TEST_APP_ID, 10))
                .thenReturn(List.of(createImageVO("bg.png", null, "https://cos.example.com/bg.png")));
        when(skillsLoader.getSkillByName("design-tokens"))
                .thenReturn(new SkillMeta("design-tokens", "设计令牌", "颜色变量"));

        DecorateContext context = DecorateContext.builder()
                .appId(TEST_APP_ID)
                .skillNames(List.of("design-tokens"))
                .build();

        // Act
        String result = decoratorChain.decorate(TEST_MESSAGE, context);

        // Assert
        // 执行顺序：Image(@Order 10) → Skill(@Order 20)
        // 每个装饰器向消息头部追加，后执行的 Skill 出现在最前面
        // 结果：Skill内容 → Image内容 → 原始消息
        int skillIdx = result.indexOf("【已启用的设计规范与最佳实践】");
        int imageIdx = result.indexOf("【可用的图片资源】");
        int messageIdx = result.indexOf(TEST_MESSAGE);

        assertTrue(imageIdx >= 0, "应包含图片上下文");
        assertTrue(skillIdx >= 0, "应包含技能上下文");
        assertTrue(skillIdx < imageIdx, "后执行的 Skill 装饰器内容应出现在最前面");
        assertTrue(imageIdx < messageIdx, "所有装饰器结果应在用户消息之前");
    }

    @Test
    @Order(13)
    void decoratorChain_withNoDecoratorsEnabled_shouldReturnOriginal() {
        // Arrange — skillNames 为空 → SkillContextDecorator.isEnabled=false
        DecorateContext context = DecorateContext.builder()
                .appId(TEST_APP_ID)
                .skillNames(List.of())
                .build();

        // Act
        String result = decoratorChain.decorate(TEST_MESSAGE, context);

        // Assert — ImageContextDecorator 有 appId 但图片为空，返回原始消息
        // 预期：ImageContextDecorator 执行（无图片→原消息），SkillContextDecorator 跳过
        assertEquals(TEST_MESSAGE, result, "无可用数据时所有装饰器应返回原始消息");
    }

    @Test
    @Order(14)
    void decoratorChain_shouldHandleMultipleDecorators() {
        // Arrange
        List<MessageDecorator> decorators = decoratorChain.getDecorators();

        // Act & Assert
        assertNotNull(decorators, "装饰器列表不应为 null");
        assertTrue(decorators.size() >= 2, "应至少注册 2 个装饰器");
        // 验证顺序：ImageContextDecorator (Order 10) → SkillContextDecorator (Order 20)
        assertTrue(decorators.get(0) instanceof ImageContextDecorator,
                "第一个装饰器应为 ImageContextDecorator");
        assertTrue(decorators.get(1) instanceof SkillContextDecorator,
                "第二个装饰器应为 SkillContextDecorator");
    }

    // ==================== 工具方法 ====================

    private AppImageVO createImageVO(String name, String description, String url) {
        AppImageVO vo = new AppImageVO();
        vo.setOriginalName(name);
        vo.setDescription(description);
        vo.setCosUrl(url);
        return vo;
    }
}
