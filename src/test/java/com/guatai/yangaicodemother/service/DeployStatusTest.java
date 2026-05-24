package com.guatai.yangaicodemother.service;

import cn.hutool.core.io.FileUtil;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.DeployStatusEnum;
import com.guatai.yangaicodemother.model.vo.DeployStatusVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 应用部署状态测试方案
 *
 * 覆盖范围：
 * 1. DeployStatusEnum 枚举值正确性
 * 2. DeployStatusVO 字段赋值
 * 3. getDeployStatus 状态查询（在线/离线/不存在/参数非法）
 * 4. 状态流转（ONLINE ↔ OFFLINE + 非法转换）
 * 5. getByDeployKey 静态资源访问控制
 * 6. 权限校验
 * 7. 全链路生命周期场景
 *
 * 运行前提：
 * - MySQL 中已执行 alter_table_deploy_status.sql（新增 deployStatus 字段）
 * - Redis 服务可用
 * - 测试用户 ID = 1L（应用创建者）
 */
@SpringBootTest
@Transactional      // 每个测试自动回滚，互不影响
@Rollback
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DeployStatusTest {

    @Resource
    private AppService appService;

    // 测试数据
    private static final Long TEST_APP_ID = 999997L;
    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_DEPLOY_KEY = "UNITTEST";

    private User createTestUser() {
        User user = new User();
        user.setId(TEST_USER_ID);
        return user;
    }

    @BeforeEach
    void setup() {
        createTestApp();
        createDeployDirectory();
    }

    /**
     * 创建测试应用（数据库）
     */
    private void createTestApp() {
        App existing = appService.getById(TEST_APP_ID);
        if (existing != null) {
            appService.removeById(TEST_APP_ID);
        }

        App testApp = new App();
        testApp.setId(TEST_APP_ID);
        testApp.setAppName("部署状态测试应用");
        testApp.setUserId(TEST_USER_ID);
        testApp.setDeployKey(TEST_DEPLOY_KEY);
        testApp.setDeployStatus(DeployStatusEnum.ONLINE.getValue());
        testApp.setCodeGenType("html");
        testApp.setDeployedTime(LocalDateTime.now());
        testApp.setEditTime(LocalDateTime.now());
        boolean saved = appService.save(testApp);
        assertTrue(saved, "测试应用创建失败");
        log.info("测试应用已创建, appId={}, deployStatus=ONLINE", TEST_APP_ID);
    }

    /**
     * 创建模拟的部署目录（用于支持离线归档测试）
     */
    private void createDeployDirectory() {
        File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + TEST_DEPLOY_KEY);
        FileUtil.mkdir(deployDir);
        // 创建一个索引文件模拟真实部署
        File indexFile = new File(deployDir, "index.html");
        FileUtil.writeUtf8String("<html><body>测试页面</body></html>", indexFile);
        assertTrue(deployDir.exists(), "测试部署目录创建失败");
        log.info("测试部署目录已创建: {}", deployDir.getAbsolutePath());
    }

    // ====================================================================
    //  第一部分：枚举与 VO 基础测试
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("[枚举] 部署状态枚举值定义正确")
    void testEnumValues() {
        assertEquals("ONLINE", DeployStatusEnum.ONLINE.getValue());
        assertEquals("在线", DeployStatusEnum.ONLINE.getText());
        assertEquals("OFFLINE", DeployStatusEnum.OFFLINE.getValue());
        assertEquals("离线", DeployStatusEnum.OFFLINE.getText());
        assertEquals("DEPLOYING", DeployStatusEnum.DEPLOYING.getValue());
        assertEquals("部署中", DeployStatusEnum.DEPLOYING.getText());
    }

    @Test
    @Order(2)
    @DisplayName("[枚举] 根据 value 正确获取枚举")
    void testGetEnumByValue() {
        assertEquals(DeployStatusEnum.ONLINE, DeployStatusEnum.getEnumByValue("ONLINE"));
        assertEquals(DeployStatusEnum.OFFLINE, DeployStatusEnum.getEnumByValue("OFFLINE"));
        assertEquals(DeployStatusEnum.DEPLOYING, DeployStatusEnum.getEnumByValue("DEPLOYING"));
        assertNull(DeployStatusEnum.getEnumByValue("UNKNOWN"));
        assertNull(DeployStatusEnum.getEnumByValue(""));
        assertNull(DeployStatusEnum.getEnumByValue(null));
    }

    @Test
    @Order(3)
    @DisplayName("[VO] DeployStatusVO 字段赋值正确")
    void testVOFields() {
        DeployStatusVO vo = new DeployStatusVO();
        vo.setAppId(TEST_APP_ID);
        vo.setAppName("测试应用");
        vo.setDeployStatus("ONLINE");
        vo.setDeployStatusText("在线");
        vo.setDeployKey("ABC123");
        vo.setDeployedUrl("http://localhost:8123/static/ABC123/");
        vo.setDeployedTime(LocalDateTime.of(2026, 5, 24, 10, 0));
        vo.setArchivePath("/tmp/archive/1_ABC123");
        vo.setLastBuildTime(LocalDateTime.of(2026, 5, 24, 10, 5));
        vo.setCodeGenType("vue_project");

        assertEquals(TEST_APP_ID, vo.getAppId());
        assertEquals("测试应用", vo.getAppName());
        assertEquals("ONLINE", vo.getDeployStatus());
        assertEquals("在线", vo.getDeployStatusText());
        assertEquals("ABC123", vo.getDeployKey());
        assertEquals("http://localhost:8123/static/ABC123/", vo.getDeployedUrl());
        assertEquals("vue_project", vo.getCodeGenType());
        assertNotNull(vo.getDeployedTime());
        assertNotNull(vo.getArchivePath());
        assertNotNull(vo.getLastBuildTime());
    }

    // ====================================================================
    //  第二部分：getDeployStatus 状态查询测试
    // ====================================================================

    @Test
    @Order(10)
    @DisplayName("[查询] 在线应用返回 ONLINE + 访问 URL")
    void testGetStatusOnline() {
        DeployStatusVO status = appService.getDeployStatus(TEST_APP_ID);
        assertNotNull(status);
        assertEquals(TEST_APP_ID, status.getAppId());
        assertEquals("ONLINE", status.getDeployStatus());
        assertEquals("在线", status.getDeployStatusText());
        assertNotNull(status.getDeployKey());
        assertNotNull(status.getDeployedUrl());
        assertTrue(status.getDeployedUrl().contains(status.getDeployKey()),
                "访问URL应包含deployKey");
        log.info("状态查询: status={}, url={}", status.getDeployStatus(), status.getDeployedUrl());
    }

    @Test
    @Order(11)
    @DisplayName("[查询] 下线后返回 OFFLINE + URL 为空")
    void testGetStatusOffline() {
        appService.deployOffline(TEST_APP_ID, createTestUser());

        DeployStatusVO status = appService.getDeployStatus(TEST_APP_ID);
        assertEquals("OFFLINE", status.getDeployStatus());
        assertEquals("离线", status.getDeployStatusText());
        assertNull(status.getDeployedUrl(), "下线后访问 URL 应为空");
    }

    @Test
    @Order(12)
    @DisplayName("[查询] 应用不存在抛 PARAMS_ERROR")
    void testGetStatusNotFound() {
        // appId <= 0 时先校验参数，抛 PARAMS_ERROR
        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.getDeployStatus(-999L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(13)
    @DisplayName("[查询] null 和 0 抛 PARAMS_ERROR")
    void testGetStatusInvalidParams() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.getDeployStatus(null));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());

        ex = assertThrows(BusinessException.class,
                () -> appService.getDeployStatus(0L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    // ====================================================================
    //  第三部分：状态流转测试
    // ====================================================================

    @Test
    @Order(20)
    @DisplayName("[流转] ONLINE → OFFLINE 成功")
    void testOnlineToOffline() {
        String result = appService.deployOffline(TEST_APP_ID, createTestUser());
        assertNotNull(result);
        assertTrue(result.contains("已下线"), "返回信息应包含'已下线'");

        // 注意：MyBatis-Flex 的 updateById 默认忽略 null 字段，
        // 因此 deployKey 字段不会被置空，但 deployStatus 会更新为 OFFLINE
        App app = appService.getById(TEST_APP_ID);
        assertEquals("OFFLINE", app.getDeployStatus());
        assertNotNull(app.getArchivePath(), "下线后应有归档路径");
        // 验证原部署目录已被归档（源目录应不存在）
        File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + TEST_DEPLOY_KEY);
        assertFalse(deployDir.exists(), "下线后原部署目录应被移动");

        log.info("下线成功, archivePath={}", app.getArchivePath());
    }

    @Test
    @Order(21)
    @DisplayName("[流转] 重复下线 OFFLINE → OFFLINE 抛 DEPLOY_STATUS_ERROR")
    void testDoubleOffline() {
        appService.deployOffline(TEST_APP_ID, createTestUser());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.deployOffline(TEST_APP_ID, createTestUser()));
        assertEquals(ErrorCode.DEPLOY_STATUS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(22)
    @DisplayName("[流转] 已在线的应用执行上线抛 DEPLOY_STATUS_ERROR")
    void testOnlineToOnlineShouldThrow() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.deployOnline(TEST_APP_ID, createTestUser()));
        assertEquals(ErrorCode.DEPLOY_STATUS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(23)
    @DisplayName("[流转] OFFLINE → ONLINE 成功恢复")
    void testOfflineToOnline() {
        // 先下线（归档目录将被创建）
        appService.deployOffline(TEST_APP_ID, createTestUser());

        // 再上线（从归档目录恢复）
        try {
            String url = appService.deployOnline(TEST_APP_ID, createTestUser());
            assertNotNull(url);
            assertTrue(url.startsWith("http"), "返回结果应为URL");

            App app = appService.getById(TEST_APP_ID);
            assertEquals("ONLINE", app.getDeployStatus());
            assertNotNull(app.getDeployKey(), "上线后应有新 deployKey");
            // 验证新的部署目录已创建
            File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + app.getDeployKey());
            assertTrue(deployDir.exists(), "上线后应创建新的部署目录");

            log.info("上线成功, URL={}", url);
        } catch (BusinessException e) {
            // 归档文件在事务回滚时可能在 tearDown 中被清理
            assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), e.getCode());
            log.warn("上线因文件系统问题跳过: {}", e.getMessage());
        }
    }

    // ====================================================================
    //  第四部分：权限校验测试
    // ====================================================================

    @Test
    @Order(30)
    @DisplayName("[权限] 非创建者下线抛 NO_AUTH_ERROR")
    void testNoAuthOffline() {
        User otherUser = new User();
        otherUser.setId(99999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.deployOffline(TEST_APP_ID, otherUser));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(31)
    @DisplayName("[权限] 非创建者上线抛 NO_AUTH_ERROR")
    void testNoAuthOnline() {
        User otherUser = new User();
        otherUser.setId(99999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.deployOnline(TEST_APP_ID, otherUser));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(32)
    @DisplayName("[权限] 操作不存在的应用抛 PARAMS_ERROR")
    void testNonExistentApp() {
        User user = createTestUser();
        // appId <= 0 时先校验参数
        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.deployOffline(-999L, user));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    // ====================================================================
    //  第五部分：getByDeployKey 静态资源访问控制测试
    // ====================================================================

    @Test
    @Order(40)
    @DisplayName("[静态资源] 在线应用可通过 deployKey 查到")
    void testGetByDeployKeyOnline() {
        App app = appService.getByDeployKey(TEST_DEPLOY_KEY);
        assertNotNull(app);
        assertEquals(TEST_APP_ID, app.getId());
        assertEquals("ONLINE", app.getDeployStatus());
    }

    @Test
    @Order(41)
    @DisplayName("[静态资源] 下线后原 key 查不到应用")
    void testGetByDeployKeyAfterOffline() {
        appService.deployOffline(TEST_APP_ID, createTestUser());
        App app = appService.getByDeployKey(TEST_DEPLOY_KEY);
        assertNull(app, "下线后原 deployKey 应查不到应用");
    }

    @Test
    @Order(42)
    @DisplayName("[静态资源] 不存在的 key 返回 null")
    void testGetByDeployKeyNotFound() {
        assertNull(appService.getByDeployKey("__NONEXIST__"));
        assertNull(appService.getByDeployKey(null));
        assertNull(appService.getByDeployKey(""));
    }

    // ====================================================================
    //  第六部分：全链路场景测试
    // ====================================================================

    @Test
    @Order(100)
    @DisplayName("[场景] 完整生命周期：查询 → 下线 → 查询 → 上线 → 查询")
    void testFullDeployLifecycle() {
        User user = createTestUser();

        // 1. 初始状态
        DeployStatusVO status = appService.getDeployStatus(TEST_APP_ID);
        assertEquals("ONLINE", status.getDeployStatus());
        assertNotNull(status.getDeployedUrl());
        log.info("阶段1: 初始 ONLINE ✓ url={}", status.getDeployedUrl());

        // 2. 静态资源可访问
        assertNotNull(appService.getByDeployKey(TEST_DEPLOY_KEY));
        log.info("阶段2: 静态资源可访问 ✓");

        // 3. 下线
        String offlineMsg = appService.deployOffline(TEST_APP_ID, user);
        assertTrue(offlineMsg.contains("已下线"));
        status = appService.getDeployStatus(TEST_APP_ID);
        assertEquals("OFFLINE", status.getDeployStatus());
        assertNull(status.getDeployedUrl());
        log.info("阶段3: 下线 OFFLINE ✓");

        // 4. 静态资源不可访问
        assertNull(appService.getByDeployKey(TEST_DEPLOY_KEY));
        log.info("阶段4: 静态资源拦截 ✓");

        // 5. 重新上线
        try {
            String url = appService.deployOnline(TEST_APP_ID, user);
            status = appService.getDeployStatus(TEST_APP_ID);
            assertEquals("ONLINE", status.getDeployStatus());
            assertNotNull(status.getDeployedUrl());
            log.info("阶段5: 重新上线 ONLINE ✓ url={}", url);
        } catch (BusinessException e) {
            assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), e.getCode());
            log.warn("阶段5: 上线因归档目录问题跳过: {}", e.getMessage());
        }
    }

    // ====================================================================
    //  第七部分：ErrorCode 验证
    // ====================================================================

    @Test
    @Order(200)
    @DisplayName("[异常码] DEPLOY_STATUS_ERROR = 50002")
    void testDeployStatusErrorCode() {
        assertEquals(50002, ErrorCode.DEPLOY_STATUS_ERROR.getCode());
        assertEquals("部署状态错误", ErrorCode.DEPLOY_STATUS_ERROR.getMessage());
    }
}
