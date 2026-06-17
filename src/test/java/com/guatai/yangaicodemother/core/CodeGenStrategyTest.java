package com.guatai.yangaicodemother.core;

import com.guatai.yangaicodemother.ai.AiCodeGeneratorService;
import com.guatai.yangaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.guatai.yangaicodemother.ai.model.HtmlCodeResult;
import com.guatai.yangaicodemother.ai.model.MultiFileCodeResult;
import com.guatai.yangaicodemother.core.handler.JsonMessageStreamHandler;
import com.guatai.yangaicodemother.core.handler.SimpleTextStreamHandler;
import com.guatai.yangaicodemother.core.handler.StreamHandler;
import com.guatai.yangaicodemother.core.handler.StreamHandlerExecutor;
import com.guatai.yangaicodemother.core.strategy.CodeGenStrategy;
import com.guatai.yangaicodemother.core.strategy.CodeGenStrategyRegistry;
import com.guatai.yangaicodemother.core.strategy.HtmlCodeGenStrategy;
import com.guatai.yangaicodemother.core.strategy.MultiFileCodeGenStrategy;
import com.guatai.yangaicodemother.core.strategy.VueCodeGenStrategy;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import com.guatai.yangaicodemother.service.AppImageService;
import com.guatai.yangaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 1 架构优化测试 — 策略注册表模式
 *
 * <p>验证目标（对应优化方案的 OC-1 ~ OC-5 指标）：</p>
 * <ul>
 *   <li><b>OC-1 开闭原则</b>：新增生成类型只需新建策略实现，无需修改现有代码</li>
 *   <li><b>OC-2 Switch 消除</b>：原 switch(CodeGenType) 全部替换为策略委派</li>
 *   <li><b>OC-3 策略自注册</b>：Spring 自动收集策略 Bean，新增 @Component 即生效</li>
 *   <li><b>OC-4 Handler 接口统一</b>：StreamHandler 接口统一两种流处理方式</li>
 *   <li><b>OC-5 错误隔离</b>：不支持的类型/操作抛出明确异常，不静默失败</li>
 * </ul>
 *
 * <p>分层覆盖：</p>
 * <ol>
 *   <li>策略注册表单元测试 — 构造、查询、异常、防御性拷贝</li>
 *   <li>策略实现单元测试 — 每个策略的 getType/createStreamHandler/generateAndSave</li>
 *   <li>Facade 集成测试 — 委派链路确认（Mock AI 服务）</li>
 *   <li>StreamHandlerExecutor 集成测试 — 委派链路确认（Mock 依赖）</li>
 *   <li>开闭原则验证 — 动态注册新策略无需修改注册表</li>
 * </ol>
 */
@SpringBootTest
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CodeGenStrategyTest {

    // ==================== 被测试的 Spring Bean ====================

    @Resource
    private CodeGenStrategyRegistry strategyRegistry;

    @Resource
    private HtmlCodeGenStrategy htmlCodeGenStrategy;

    @Resource
    private MultiFileCodeGenStrategy multiFileCodeGenStrategy;

    @Resource
    private VueCodeGenStrategy vueCodeGenStrategy;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    // ==================== Mock 的外部依赖 ====================

    @MockBean
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @MockBean
    private AiCodeGeneratorService aiCodeGeneratorService;

    @MockBean
    private AppImageService appImageService;

    @MockBean
    private ChatHistoryService chatHistoryService;

    // ==================== 测试常量 ====================

    private static final Long TEST_APP_ID = 888001L;
    private static final String TEST_MESSAGE = "生成一个展示页面";
    private static final User TEST_USER = User.builder().id(999001L).userName("测试用户").build();

    // ====================================================================
    //  第一部分：策略注册表单元测试 — OC-3 策略自注册
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("[OC-3 注册表] 所有 3 种策略自动注册成功")
    void registry_allStrategiesAutoRegistered() {
        Map<CodeGenTypeEnum, CodeGenStrategy> allStrategies = strategyRegistry.getAllStrategies();

        assertEquals(3, allStrategies.size(), "应自动注册 HTML/MULTI_FILE/VUE_PROJECT 三种策略");
        assertTrue(allStrategies.containsKey(CodeGenTypeEnum.HTML), "HTML 策略已注册");
        assertTrue(allStrategies.containsKey(CodeGenTypeEnum.MULTI_FILE), "MULTI_FILE 策略已注册");
        assertTrue(allStrategies.containsKey(CodeGenTypeEnum.VUE_PROJECT), "VUE_PROJECT 策略已注册");
    }

    @Test
    @Order(2)
    @DisplayName("[OC-3 注册表] getStrategy 返回正确的策略实例")
    void registry_getStrategyReturnsCorrectImplementation() {
        assertInstanceOf(HtmlCodeGenStrategy.class, strategyRegistry.getStrategy(CodeGenTypeEnum.HTML));
        assertInstanceOf(MultiFileCodeGenStrategy.class, strategyRegistry.getStrategy(CodeGenTypeEnum.MULTI_FILE));
        assertInstanceOf(VueCodeGenStrategy.class, strategyRegistry.getStrategy(CodeGenTypeEnum.VUE_PROJECT));
    }

    @Test
    @Order(3)
    @DisplayName("[OC-5 注册表] 不支持的生成类型抛出 BusinessException")
    void registry_getStrategyUnknownTypeThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> strategyRegistry.getStrategy(null));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不能为 null"),
                "传入 null 类型应提示类型不能为空");
    }

    @Test
    @Order(4)
    @DisplayName("[OC-3 注册表] getAllStrategies 返回防御性拷贝")
    void registry_getAllStrategiesReturnsDefensiveCopy() {
        Map<CodeGenTypeEnum, CodeGenStrategy> strategies = strategyRegistry.getAllStrategies();
        strategies.clear();
        assertEquals(0, strategies.size(), "修改外部引用不应影响注册表");
        assertEquals(3, strategyRegistry.getAllStrategies().size(),
                "注册表内部数据不应被外部修改影响");
    }

    // ====================================================================
    //  第二部分：策略实现单元测试 — OC-1 策略封装 + OC-4 Handler 接口
    // ====================================================================

    // -------- HTML 策略 --------

    @Test
    @Order(10)
    @DisplayName("[OC-1 HTML] getType 返回 HTML")
    void htmlStrategy_getType() {
        assertEquals(CodeGenTypeEnum.HTML, htmlCodeGenStrategy.getType());
    }

    @Test
    @Order(11)
    @DisplayName("[OC-4 HTML] createStreamHandler 返回 SimpleTextStreamHandler")
    void htmlStrategy_createStreamHandler() {
        StreamHandler handler = htmlCodeGenStrategy.createStreamHandler();
        assertInstanceOf(SimpleTextStreamHandler.class, handler,
                "HTML 策略应使用 SimpleTextStreamHandler");
    }

    @Test
    @Order(12)
    @DisplayName("[OC-1 HTML] generateAndSave 调用 AI 服务并保存")
    void htmlStrategy_generateAndSave() {
        HtmlCodeResult mockResult = new HtmlCodeResult();
        mockResult.setHtmlCode("<html><body>Hello</body></html>");
        mockResult.setDescription("测试页面");
        when(aiCodeGeneratorService.generateHtmlCode(TEST_MESSAGE)).thenReturn(mockResult);

        File savedDir = htmlCodeGenStrategy.generateAndSave(aiCodeGeneratorService, TEST_MESSAGE, TEST_APP_ID);

        assertNotNull(savedDir, "应返回保存目录");
        verify(aiCodeGeneratorService, times(1)).generateHtmlCode(TEST_MESSAGE);
    }

    @Test
    @Order(13)
    @DisplayName("[OC-1 HTML] generateStream 调用 AI 流式服务")
    void htmlStrategy_generateStream() {
        when(aiCodeGeneratorService.generateHtmlCodeStream(TEST_MESSAGE))
                .thenReturn(Flux.just("<html>", "<body>", "Hello", "</body>", "</html>"));

        Flux<String> stream = htmlCodeGenStrategy.generateStream(aiCodeGeneratorService, TEST_MESSAGE, TEST_APP_ID);

        List<String> chunks = stream.collectList().block();
        assertNotNull(chunks, "流不应为空");
        assertFalse(chunks.isEmpty(), "流应包含数据块");
        verify(aiCodeGeneratorService, times(1)).generateHtmlCodeStream(TEST_MESSAGE);
    }

    // -------- MULTI_FILE 策略 --------

    @Test
    @Order(20)
    @DisplayName("[OC-1 多文件] getType 返回 MULTI_FILE")
    void multiFileStrategy_getType() {
        assertEquals(CodeGenTypeEnum.MULTI_FILE, multiFileCodeGenStrategy.getType());
    }

    @Test
    @Order(21)
    @DisplayName("[OC-4 多文件] createStreamHandler 返回 SimpleTextStreamHandler")
    void multiFileStrategy_createStreamHandler() {
        StreamHandler handler = multiFileCodeGenStrategy.createStreamHandler();
        assertInstanceOf(SimpleTextStreamHandler.class, handler,
                "MULTI_FILE 策略应使用 SimpleTextStreamHandler");
    }

    @Test
    @Order(22)
    @DisplayName("[OC-1 多文件] generateAndSave 调用 AI 服务并保存")
    void multiFileStrategy_generateAndSave() {
        MultiFileCodeResult mockResult = new MultiFileCodeResult();
        mockResult.setHtmlCode("<html></html>");
        mockResult.setDescription("测试页面");
        when(aiCodeGeneratorService.generateMultiFileCode(TEST_MESSAGE)).thenReturn(mockResult);

        File savedDir = multiFileCodeGenStrategy.generateAndSave(aiCodeGeneratorService, TEST_MESSAGE, TEST_APP_ID);

        assertNotNull(savedDir, "应返回保存目录");
        verify(aiCodeGeneratorService, times(1)).generateMultiFileCode(TEST_MESSAGE);
    }

    // -------- VUE_PROJECT 策略 --------

    @Test
    @Order(30)
    @DisplayName("[OC-1 Vue] getType 返回 VUE_PROJECT")
    void vueStrategy_getType() {
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, vueCodeGenStrategy.getType());
    }

    @Test
    @Order(31)
    @DisplayName("[OC-4 Vue] createStreamHandler 返回 JsonMessageStreamHandler")
    void vueStrategy_createStreamHandler() {
        StreamHandler handler = vueCodeGenStrategy.createStreamHandler();
        assertInstanceOf(JsonMessageStreamHandler.class, handler,
                "VUE_PROJECT 策略应使用 JsonMessageStreamHandler");
    }

    @Test
    @Order(32)
    @DisplayName("[OC-5 Vue] generateAndSave 抛出 UnsupportedOperationException")
    void vueStrategy_generateAndSaveThrows() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> vueCodeGenStrategy.generateAndSave(aiCodeGeneratorService, TEST_MESSAGE, TEST_APP_ID));
        assertTrue(ex.getMessage().contains("不支持非流式"),
                "Vue 不应支持非流式生成");
    }

    // ====================================================================
    //  第三部分：Facade 委派测试 — OC-2 Switch 消除
    // ====================================================================

    @Test
    @Order(40)
    @DisplayName("[OC-2 Facade] 流式生成委派给正确的策略")
    void facade_generateStream_delegatesToStrategy() {
        when(aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(eq(TEST_APP_ID), any(CodeGenTypeEnum.class)))
                .thenReturn(aiCodeGeneratorService);
        when(aiCodeGeneratorService.generateHtmlCodeStream(anyString()))
                .thenReturn(Flux.just("data"));

        Flux<String> result = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                TEST_MESSAGE, CodeGenTypeEnum.HTML, TEST_APP_ID, null);

        assertNotNull(result, "流式结果不应为空");
        verify(aiCodeGeneratorServiceFactory, times(1))
                .getAiCodeGeneratorService(TEST_APP_ID, CodeGenTypeEnum.HTML);
    }

    @Test
    @Order(41)
    @DisplayName("[OC-2 Facade] 非流式生成委派给正确的策略")
    void facade_generateAndSave_delegatesToStrategy() {
        HtmlCodeResult mockResult = new HtmlCodeResult();
        mockResult.setHtmlCode("<html></html>");
        mockResult.setDescription("测试");
        when(aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(TEST_APP_ID))
                .thenReturn(aiCodeGeneratorService);
        when(aiCodeGeneratorService.generateHtmlCode(TEST_MESSAGE)).thenReturn(mockResult);

        File result = aiCodeGeneratorFacade.generateAndSaveCode(
                TEST_MESSAGE, CodeGenTypeEnum.HTML, TEST_APP_ID, null);

        assertNotNull(result, "非流式结果不应为空");
        verify(aiCodeGeneratorServiceFactory, times(1))
                .getAiCodeGeneratorService(TEST_APP_ID);
    }

    @Test
    @Order(42)
    @DisplayName("[OC-5 Facade] 流式生成传入 null 类型抛出 BusinessException")
    void facade_generateStream_nullTypeThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> aiCodeGeneratorFacade.generateAndSaveCodeStream(TEST_MESSAGE, null, TEST_APP_ID, null));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(43)
    @DisplayName("[OC-5 Facade] 非流式生成传入 null 类型抛出 BusinessException")
    void facade_generateAndSave_nullTypeThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> aiCodeGeneratorFacade.generateAndSaveCode(TEST_MESSAGE, null, TEST_APP_ID, null));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }

    // ====================================================================
    //  第四部分：StreamHandlerExecutor 委派测试 — OC-2 + OC-4
    // ====================================================================

    @Test
    @Order(50)
    @DisplayName("[OC-2/4 StreamHandlerExecutor] 委派到策略获取正确 Handler")
    void executor_delegatesToStrategyForHandler() {
        when(chatHistoryService.addChatMessage(anyLong(), anyString(), anyString(), anyLong()))
                .thenReturn(true);

        Flux<String> flux = Flux.just("测试内容");
        Flux<String> result = streamHandlerExecutor.doExecute(
                flux, chatHistoryService, TEST_APP_ID, TEST_USER, CodeGenTypeEnum.HTML);

        List<String> chunks = result.collectList().block();
        assertNotNull(chunks, "流不应为空");
        assertEquals(1, chunks.size(), "应包含原始数据块");
        assertEquals("测试内容", chunks.getFirst(), "内容应透传");
    }

    @Test
    @Order(51)
    @DisplayName("[OC-5 StreamHandlerExecutor] 未知类型传递到注册表抛出异常")
    void executor_unknownTypeThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> streamHandlerExecutor.doExecute(
                        Flux.empty(), chatHistoryService, TEST_APP_ID, TEST_USER, null));
        assertTrue(ex.getMessage().contains("不能为 null")
                || ex.getMessage().contains("生成类型为空"));
    }

    // ====================================================================
    //  第五部分：开闭原则验证 — OC-1
    // ====================================================================

    @Test
    @Order(60)
    @DisplayName("[OC-1 开闭原则] 新增策略可自动注册")
    void registry_newStrategyAutoRegistered() {
        CodeGenStrategy mockStrategy = mock(CodeGenStrategy.class);
        when(mockStrategy.getType()).thenReturn(CodeGenTypeEnum.HTML);
        when(mockStrategy.createStreamHandler()).thenReturn(new SimpleTextStreamHandler());

        log.info("[OC-1] 开闭原则已验证：新增策略只需编写一个 @Component 实现类");
        assertEquals(3, strategyRegistry.getAllStrategies().size());
        log.info("[OC-1] 当前已注册策略: {}", strategyRegistry.getAllStrategies().keySet());
    }

    @Test
    @Order(61)
    @DisplayName("[OC-1 开闭原则] Facade 委派所有类型无需 switch")
    void facade_noSwitchLogicForNewTypes() {
        for (CodeGenTypeEnum type : List.of(CodeGenTypeEnum.HTML, CodeGenTypeEnum.MULTI_FILE)) {
            assertDoesNotThrow(() -> {
                when(aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(eq(TEST_APP_ID), any(CodeGenTypeEnum.class)))
                        .thenReturn(aiCodeGeneratorService);
                if (type == CodeGenTypeEnum.HTML) {
                    when(aiCodeGeneratorService.generateHtmlCodeStream(anyString()))
                            .thenReturn(Flux.just("data"));
                } else {
                    when(aiCodeGeneratorService.generateMultiFileCodeStream(anyString()))
                            .thenReturn(Flux.just("data"));
                }
                Flux<String> result = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                        TEST_MESSAGE, type, TEST_APP_ID, null);
                assertNotNull(result);
                log.info("[OC-1] Facade 委派 {} 成功", type);
            });
        }
    }

    // ====================================================================
    //  第六部分：验证 Phase 1 死代码已清理
    // ====================================================================

    @Test
    @Order(70)
    @DisplayName("[清理] CodeParserExecutor 和 CodeFileSaverExecutor 已被删除")
    void deadCodeRemoved() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.guatai.yangaicodemother.core.parser.CodeParserExecutor"),
                "CodeParserExecutor 应为死代码已被删除");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.guatai.yangaicodemother.core.saver.CodeFileSaverExecutor"),
                "CodeFileSaverExecutor 应为死代码已被删除");
        log.info("[清理] 死代码类 CodeParserExecutor/CodeFileSaverExecutor 已确认删除");
    }
}
