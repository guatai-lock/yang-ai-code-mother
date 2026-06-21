package com.guatai.yangaicodemother.core.pipeline;

import com.guatai.yangaicodemother.ai.guardrail.PromptRewriteService;
import com.guatai.yangaicodemother.mapper.AppMapper;
import com.guatai.yangaicodemother.model.dto.app.ChatToGenCodeRequest;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import com.guatai.yangaicodemother.model.enums.DeployStatusEnum;
import com.guatai.yangaicodemother.monitor.MonitorContextHolder;
import com.guatai.yangaicodemother.rag.RagSwitchHolder;
import com.guatai.yangaicodemother.service.ChatHistoryService;
import com.guatai.yangaicodemother.service.FeaturedAppApplicationService;
import com.guatai.yangaicodemother.core.pipeline.stage.ChatHistoryStage;
import com.guatai.yangaicodemother.core.pipeline.stage.ContentReviewStage;
import com.guatai.yangaicodemother.core.pipeline.stage.MonitorStage;
import com.guatai.yangaicodemother.core.pipeline.stage.PromptRewriteStage;
import com.guatai.yangaicodemother.core.pipeline.stage.RagSwitchStage;
import com.guatai.yangaicodemother.core.pipeline.stage.ValidationStage;
import com.guatai.yangaicodemother.core.pipeline.lifecycle.MonitorContextLifecycle;
import com.guatai.yangaicodemother.core.pipeline.lifecycle.RagSwitchContextLifecycle;
import com.guatai.yangaicodemother.monitor.MonitorContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.ExecutorService;

import reactor.core.publisher.SignalType;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 3 + Phase 4 管道模式测试
 * <ol>
 *   <li>GenPipeline 初始化和编排</li>
 *   <li>PipelineContextManager + ContextLifecycle 初始化和编排</li>
 *   <li>ValidationStage 独立行为</li>
 *   <li>RagSwitchContextLifecycle ThreadLocal 管理</li>
 *   <li>ChatHistoryStage 持久化</li>
 *   <li>MonitorStage + MonitorContextLifecycle 上下文管理</li>
 *   <li>PromptRewriteStage 重写</li>
 *   <li>ContentReviewStage 条件触发</li>
 *   <li>Pipeline Context 便捷访问器</li>
 * </ol>
 */
@SpringBootTest
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GenPipelineTest {

    private static final Long TEST_APP_ID = 999992L;
    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_MESSAGE = "生成一个登录页面";

    // ==================== Mock 的外部依赖 ====================

    @MockBean
    private AppMapper appMapper;

    @MockBean
    private ChatHistoryService chatHistoryService;

    @MockBean
    private PromptRewriteService promptRewriteService;

    @MockBean
    private FeaturedAppApplicationService featuredAppApplicationService;

    @MockBean(name = "virtualThreadExecutor")
    private ExecutorService virtualThreadExecutor;

    // ==================== 被测试的 Bean ====================

    @Resource
    private GenPipeline genPipeline;

    @Resource
    private PipelineContextManager pipelineContextManager;

    @Resource
    private ValidationStage validationStage;

    @Resource
    private RagSwitchStage ragSwitchStage;

    @Resource
    private RagSwitchContextLifecycle ragSwitchContextLifecycle;

    @Resource
    private ChatHistoryStage chatHistoryStage;

    @Resource
    private MonitorStage monitorStage;

    @Resource
    private MonitorContextLifecycle monitorContextLifecycle;

    @Resource
    private PromptRewriteStage promptRewriteStage;

    @Resource
    private ContentReviewStage contentReviewStage;

    // ==================== 测试数据 ====================

    private User createTestUser() {
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setUserName("test");
        return user;
    }

    private App createTestApp(String codeGenType, Integer priority, String deployStatus) {
        App app = new App();
        app.setId(TEST_APP_ID);
        app.setUserId(TEST_USER_ID);
        app.setCodeGenType(codeGenType);
        app.setPriority(priority);
        app.setDeployStatus(deployStatus);
        app.setConversationRound(0);
        return app;
    }

    private ChatToGenCodeRequest createRequest(String message, Boolean ragEnabled, List<String> skillNames) {
        ChatToGenCodeRequest request = new ChatToGenCodeRequest();
        request.setAppId(TEST_APP_ID);
        request.setMessage(message);
        request.setRagEnabled(ragEnabled);
        request.setSkillNames(skillNames);
        return request;
    }

    // ==================== 第一部分：Pipeline 初始化 ====================

    @Test
    @Order(1)
    void pipeline_shouldAutoRegisterAllStages() {
        // Arrange
        List<GenStage> stages = genPipeline.getStages();

        // Assert
        assertNotNull(stages, "Stage 列表不应为 null");
        assertTrue(stages.size() >= 6, "应至少注册 6 个 Stage");
    }

    @Test
    @Order(2)
    void pipeline_shouldSortStagesByOrder() {
        // Arrange
        List<GenStage> stages = genPipeline.getStages();

        // Assert
        assertInstanceOf(ValidationStage.class, stages.get(0), "第一个应为 ValidationStage(@Order 10)");
        assertInstanceOf(RagSwitchStage.class, stages.get(1), "第二个应为 RagSwitchStage(@Order 20)");
        assertInstanceOf(ChatHistoryStage.class, stages.get(2), "第三个应为 ChatHistoryStage(@Order 30)");
        assertInstanceOf(MonitorStage.class, stages.get(3), "第四个应为 MonitorStage(@Order 40)");
        assertInstanceOf(PromptRewriteStage.class, stages.get(4), "第五个应为 PromptRewriteStage(@Order 50)");
        assertInstanceOf(ContentReviewStage.class, stages.get(5), "第六个应为 ContentReviewStage(@Order 60)");
    }

    // ==================== 第二部分：PipelineContextManager ====================

    @Test
    @Order(3)
    void pipelineContextManager_shouldAutoRegisterContextLifecycles() {
        // Arrange
        List<ContextLifecycle> handlers = pipelineContextManager.getHandlers();

        // Assert
        assertNotNull(handlers, "ContextLifecycle 列表不应为 null");
        assertTrue(handlers.size() >= 2, "应至少注册 2 个 ContextLifecycle");
    }

    @Test
    @Order(4)
    void pipelineContextManager_shouldSortLifecyclesByOrder() {
        // Arrange
        List<ContextLifecycle> handlers = pipelineContextManager.getHandlers();

        // Assert — RagSwitchContextLifecycle(@Order 20) 应在 MonitorContextLifecycle(@Order 40) 之前
        int ragIdx = -1, monIdx = -1;
        for (int i = 0; i < handlers.size(); i++) {
            if (handlers.get(i) instanceof RagSwitchContextLifecycle) ragIdx = i;
            if (handlers.get(i) instanceof MonitorContextLifecycle) monIdx = i;
        }
        assertTrue(ragIdx >= 0, "应找到 RagSwitchContextLifecycle");
        assertTrue(monIdx >= 0, "应找到 MonitorContextLifecycle");
        assertTrue(ragIdx < monIdx, "RagSwitchContextLifecycle(@Order 20) 应在 MonitorContextLifecycle(@Order 40) 之前");
    }

    @Test
    @Order(5)
    void pipelineContextManager_shouldSetupAndClearAllLifecycles() {
        // Arrange
        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, true, null),
                createTestUser()
        );
        // 模拟 MonitorStage 已设置 monitorContext
        ctx.setMonitorContext(MonitorContext.builder()
                .appId(TEST_APP_ID.toString())
                .userId(TEST_USER_ID.toString())
                .build());

        // Act — setup all
        pipelineContextManager.setup(ctx);

        // Assert — 所有 ThreadLocal 已设置
        assertTrue(RagSwitchHolder.isEnabled(), "RAG 应启用");
        assertNotNull(MonitorContextHolder.getContext(), "MonitorContext 应已设置");

        // Act — clear all
        pipelineContextManager.clear(ctx, SignalType.ON_COMPLETE);

        // Assert — 所有 ThreadLocal 已清理
        assertFalse(RagSwitchHolder.isEnabled(), "RAG 应禁用");
        assertNull(MonitorContextHolder.getContext(), "MonitorContext 应已清理");
    }

    @Test
    @Order(6)
    void pipelineContextManager_shouldRestoreAllLifecycles() {
        // Arrange
        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, true, null),
                createTestUser()
        );
        ctx.setMonitorContext(MonitorContext.builder()
                .appId(TEST_APP_ID.toString())
                .userId(TEST_USER_ID.toString())
                .build());

        // Act — restore (模拟 doOnSubscribe 中异步线程恢复)
        pipelineContextManager.restore(ctx);

        // Assert — 所有 ThreadLocal 已恢复
        assertTrue(RagSwitchHolder.isEnabled(), "RAG 应恢复为启用");
        assertNotNull(MonitorContextHolder.getContext(), "MonitorContext 应恢复");
        assertEquals(TEST_APP_ID.toString(), MonitorContextHolder.getContext().getAppId());

        // Cleanup
        pipelineContextManager.clear(ctx, SignalType.ON_COMPLETE);
    }

    // ==================== 第三部分：ValidationStage ====================

    @Test
    @Order(10)
    void validationStage_withValidParams_shouldSetAppAndType() {
        // Arrange
        App mockApp = createTestApp(CodeGenTypeEnum.HTML.getValue(), 0, null);
        when(appMapper.selectOneById(TEST_APP_ID)).thenReturn(mockApp);

        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );

        // Act
        validationStage.execute(ctx);

        // Assert
        assertNotNull(ctx.getApp(), "应设置 app");
        assertEquals(CodeGenTypeEnum.HTML, ctx.getCodeGenTypeEnum(), "应解析代码生成类型");
        verify(appMapper).selectOneById(TEST_APP_ID);
    }

    @Test
    @Order(11)
    void validationStage_withNullAppId_shouldThrow() {
        // Arrange
        PipelineContext ctx = new PipelineContext(
                createRequest(null, false, null),
                createTestUser()
        );

        // Act & Assert
        assertThrows(Exception.class, () -> validationStage.execute(ctx));
    }

    @Test
    @Order(12)
    void validationStage_withNonExistentApp_shouldThrow() {
        // Arrange
        when(appMapper.selectOneById(TEST_APP_ID)).thenReturn(null);
        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );

        // Act & Assert
        assertThrows(Exception.class, () -> validationStage.execute(ctx));
    }

    @Test
    @Order(13)
    void validationStage_withUnauthorizedUser_shouldThrow() {
        // Arrange
        App mockApp = createTestApp(CodeGenTypeEnum.HTML.getValue(), 0, null);
        mockApp.setUserId(999L);  // 应用属于另一个用户
        when(appMapper.selectOneById(TEST_APP_ID)).thenReturn(mockApp);

        User loginUser = createTestUser();  // loginUser.id = TEST_USER_ID (1L)
        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                loginUser
        );

        // Act & Assert
        assertThrows(Exception.class, () -> validationStage.execute(ctx));
    }

    @Test
    @Order(14)
    void validationStage_withUnknownType_shouldThrow() {
        // Arrange
        App mockApp = createTestApp("unknown_type", 0, null);
        when(appMapper.selectOneById(TEST_APP_ID)).thenReturn(mockApp);

        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );

        // Act & Assert
        assertThrows(Exception.class, () -> validationStage.execute(ctx));
    }

    // ==================== 第四部分：RagSwitchContextLifecycle ====================

    @Test
    @Order(20)
    void ragSwitchContextLifecycle_withEnabled_shouldSetAndClearThreadLocal() {
        // Arrange
        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, true, null),
                createTestUser()
        );

        // Act — setup (模拟 runSetup 末尾的行为)
        ragSwitchContextLifecycle.setup(ctx);

        // Assert — ThreadLocal 已设置
        assertTrue(RagSwitchHolder.isEnabled(), "RAG 应启用");

        // Act — clear (模拟 doFinally 中的行为)
        ragSwitchContextLifecycle.clear(ctx, SignalType.ON_COMPLETE);

        // Assert — ThreadLocal 已清理
        assertFalse(RagSwitchHolder.isEnabled(), "清理后 RAG 应禁用");
    }

    @Test
    @Order(21)
    void ragSwitchContextLifecycle_withDisabled_shouldNotEnable() {
        // Arrange
        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );

        // Act
        ragSwitchContextLifecycle.setup(ctx);

        // Assert
        assertFalse(RagSwitchHolder.isEnabled(), "RAG 应禁用");

        // Cleanup
        ragSwitchContextLifecycle.clear(ctx, null);
    }

    @Test
    @Order(22)
    void ragSwitchContextLifecycle_shouldRestoreInAsyncThread() {
        // Arrange
        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, true, null),
                createTestUser()
        );

        // Act — restore (模拟 doOnSubscribe 中异步线程恢复)
        ragSwitchContextLifecycle.restore(ctx);

        // Assert — 应从 PipelineContext 恢复
        assertTrue(RagSwitchHolder.isEnabled(), "restore 后 RAG 应启用");

        // Cleanup
        ragSwitchContextLifecycle.clear(ctx, null);
    }

    // ==================== 第五部分：ChatHistoryStage ====================

    @Test
    @Order(30)
    void chatHistoryStage_shouldSaveMessageAndIncrementRound() {
        // Arrange
        App mockApp = createTestApp(CodeGenTypeEnum.HTML.getValue(), 0, null);
        mockApp.setConversationRound(5);
        when(appMapper.selectOneById(TEST_APP_ID)).thenReturn(mockApp);
        when(chatHistoryService.addChatMessage(anyLong(), anyString(), anyString(), anyLong()))
                .thenReturn(true);

        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );
        ctx.setApp(mockApp);

        // Act
        chatHistoryStage.execute(ctx);

        // Assert
        verify(chatHistoryService).addChatMessage(
                eq(TEST_APP_ID), eq(TEST_MESSAGE),
                eq(ChatHistoryMessageTypeEnum.USER.getValue()),
                eq(TEST_USER_ID)
        );
        verify(appMapper).update(argThat(updateApp ->
                updateApp.getConversationRound() != null && updateApp.getConversationRound() == 6
        ));
    }

    @Test
    @Order(31)
    void chatHistoryStage_withNullRound_shouldSetToOne() {
        // Arrange
        App mockApp = createTestApp(CodeGenTypeEnum.HTML.getValue(), 0, null);
        mockApp.setConversationRound(null);
        when(appMapper.selectOneById(TEST_APP_ID)).thenReturn(mockApp);

        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );
        ctx.setApp(mockApp);

        // Act
        chatHistoryStage.execute(ctx);

        // Assert
        verify(appMapper).update(argThat(updateApp ->
                updateApp.getConversationRound() == 1
        ));
    }

    // ==================== 第六部分：MonitorStage + MonitorContextLifecycle ====================

    @Test
    @Order(40)
    void monitorStage_shouldBuildMonitorContextInPipelineContext() {
        // Arrange
        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );

        // Act
        monitorStage.execute(ctx);

        // Assert — MonitorStage 只负责构建对象存入 PipelineContext，不再设置 ThreadLocal
        assertNotNull(ctx.getMonitorContext(), "PipelineContext 中的 monitorContext 应已设置");
        assertEquals(TEST_APP_ID.toString(), ctx.getMonitorContext().getAppId());
        assertEquals(TEST_USER_ID.toString(), ctx.getMonitorContext().getUserId());
        // Phase 4: MonitorStage 不再操作 ThreadLocal
        assertNull(MonitorContextHolder.getContext(), "MonitorStage 不应设置 ThreadLocal");
    }

    @Test
    @Order(41)
    void monitorContextLifecycle_shouldSetAndClearThreadLocal() {
        // Arrange
        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );
        // 模拟 MonitorStage 已执行：构建 MonitorContext 并存入 PipelineContext
        MonitorContext mc = MonitorContext.builder()
                .appId(TEST_APP_ID.toString())
                .userId(TEST_USER_ID.toString())
                .build();
        ctx.setMonitorContext(mc);

        // Act — setup (模拟 runSetup 末尾的行为)
        monitorContextLifecycle.setup(ctx);

        // Assert — ThreadLocal 已设置
        assertNotNull(MonitorContextHolder.getContext(), "MonitorContext 应已设置");
        assertEquals(TEST_APP_ID.toString(), MonitorContextHolder.getContext().getAppId());

        // Act — clear (模拟 doFinally 中的行为)
        monitorContextLifecycle.clear(ctx, SignalType.ON_COMPLETE);

        // Assert — ThreadLocal 已清理
        assertNull(MonitorContextHolder.getContext(), "清理后 MonitorContext 应为 null");
    }

    @Test
    @Order(42)
    void monitorContextLifecycle_shouldRestoreInAsyncThread() {
        // Arrange
        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );
        ctx.setMonitorContext(MonitorContext.builder()
                .appId(TEST_APP_ID.toString())
                .userId(TEST_USER_ID.toString())
                .build());

        // Act — restore (模拟 doOnSubscribe 中异步线程恢复)
        monitorContextLifecycle.restore(ctx);

        // Assert — 应从 PipelineContext 恢复
        assertNotNull(MonitorContextHolder.getContext(), "restore 后 MonitorContext 应恢复");
        assertEquals(TEST_APP_ID.toString(), MonitorContextHolder.getContext().getAppId());

        // Cleanup
        monitorContextLifecycle.clear(ctx, null);
    }

    // ==================== 第七部分：PromptRewriteStage ====================

    @Test
    @Order(50)
    void promptRewriteStage_shouldRewriteMessage() {
        // Arrange
        String safeMessage = "生成一个安全的登录页面";
        when(promptRewriteService.rewrite(TEST_MESSAGE)).thenReturn(safeMessage);

        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );

        // Act
        promptRewriteStage.execute(ctx);

        // Assert
        assertEquals(safeMessage, ctx.getSafeMessage(), "应设置重写后的消息");
        verify(promptRewriteService).rewrite(TEST_MESSAGE);
    }

    // ==================== 第八部分：ContentReviewStage ====================

    @Test
    @Order(60)
    void contentReviewStage_withFeaturedDeployed_shouldTriggerOnComplete() {
        // Arrange
        App mockApp = createTestApp(CodeGenTypeEnum.HTML.getValue(), 99, DeployStatusEnum.ONLINE.getValue());

        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );
        ctx.setApp(mockApp);

        // Act — execute 快照
        contentReviewStage.execute(ctx);
        assertTrue(ctx.isFeaturedDeployedApp(), "精选已部署应用应标记");

        // Act — cleanup ON_COMPLETE
        contentReviewStage.cleanup(ctx, SignalType.ON_COMPLETE);

        // Assert — 验证异步任务被提交到了虚拟线程执行器
        // 注意：实际执行由 ExecutorService 处理，单元测试验证提交行为而非执行结果
        verify(virtualThreadExecutor).execute(any(Runnable.class));
    }

    @Test
    @Order(61)
    void contentReviewStage_withNonFeatured_shouldNotTrigger() {
        // Arrange
        App mockApp = createTestApp(CodeGenTypeEnum.HTML.getValue(), 0, null);

        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );
        ctx.setApp(mockApp);

        // Act
        contentReviewStage.execute(ctx);
        assertFalse(ctx.isFeaturedDeployedApp(), "非精选应用不应标记");

        contentReviewStage.cleanup(ctx, SignalType.ON_COMPLETE);

        // Assert
        verify(virtualThreadExecutor, never()).execute(any(Runnable.class));
        verify(featuredAppApplicationService, never()).requestContentReview(anyLong(), any());
    }

    @Test
    @Order(62)
    void contentReviewStage_withFeaturedDeployed_shouldNotTriggerOnError() {
        // Arrange
        App mockApp = createTestApp(CodeGenTypeEnum.HTML.getValue(), 99, DeployStatusEnum.ONLINE.getValue());

        PipelineContext ctx = new PipelineContext(
                createRequest(TEST_MESSAGE, false, null),
                createTestUser()
        );
        ctx.setApp(mockApp);

        // Act
        contentReviewStage.execute(ctx);
        assertTrue(ctx.isFeaturedDeployedApp());

        // cleanup ON_ERROR — 不应触发审核
        contentReviewStage.cleanup(ctx, SignalType.ON_ERROR);

        // Assert
        verify(virtualThreadExecutor, never()).execute(any(Runnable.class));
        verify(featuredAppApplicationService, never()).requestContentReview(anyLong(), any());
    }

    // ==================== 第九部分：Pipeline Context ====================

    @Test
    @Order(70)
    void pipelineContext_shouldProvideConvenientAccessors() {
        // Arrange
        ChatToGenCodeRequest request = createRequest(TEST_MESSAGE, true, List.of("design-tokens"));
        User user = createTestUser();

        // Act
        PipelineContext ctx = new PipelineContext(request, user);

        // Assert
        assertEquals(TEST_APP_ID, ctx.getAppId());
        assertEquals(TEST_MESSAGE, ctx.getOriginalMessage());
        assertEquals(List.of("design-tokens"), ctx.getSkillNames());
        assertTrue(ctx.isRagEnabled());
        assertNull(ctx.getApp());
        assertNull(ctx.getCodeGenTypeEnum());
        assertNull(ctx.getSafeMessage());
        assertNull(ctx.getMonitorContext());
        assertFalse(ctx.isFeaturedDeployedApp());
    }

    // ==================== 清理：确保 ThreadLocal 不在测试间泄漏 ====================

    @AfterEach
    void cleanUpThreadLocal() {
        MonitorContextHolder.clearContext();
        RagSwitchHolder.clear();
    }
}
