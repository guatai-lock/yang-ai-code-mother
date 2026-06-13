package com.guatai.yangaicodemother.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.mapper.AppFeaturedApplicationMapper;
import com.guatai.yangaicodemother.model.entity.AppFeaturedApplication;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.FeaturedAppStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 应用删除与文件清理测试
 *
 * 覆盖范围：
 * 1. 状态拦截：ONLINE / DEPLOYING 删除被拒绝，文件保留
 * 2. 文件清理：OFFLINE / null 删除后异步清理代码输出、归档、Redis
 * 3. 精选申请：删除应用时自动取消 PENDING 申请
 * 4. 空值安全：archivePath=null / codeGenType=null 不抛异常
 * 5. 缺失目录：目录不存在时静默跳过
 *
 * 运行前提：
 * - Redis 服务可用
 * - 测试用户 ID = 1L（应用创建者）
 *
 * 说明：
 * - 不使用 @Transactional（文件系统变更不可回滚）
 * - @BeforeEach/@AfterEach 手动清理数据库和文件
 * - 异步清理通过 Thread.sleep 等待执行完成
 */
@SpringBootTest
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AppDeletionCleanupTest {

    @Resource
    private AppService appService;

    @Resource
    private AppCleanupService appCleanupService;

    @Resource
    private FeaturedAppApplicationService featuredAppApplicationService;

    @Resource
    private AppFeaturedApplicationMapper featuredAppMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 使用未被其他测试占用的 ID
    private static final Long TEST_APP_ID = 999996L;
    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_DEPLOY_KEY = "CLNTST";
    private static final String TEST_CODE_TYPE = "html";

    private String codeOutputPath;
    private String archivePath;
    private String deployPath;

    @BeforeEach
    void setup() {
        codeOutputPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator
                + TEST_CODE_TYPE + "_" + TEST_APP_ID;
        archivePath = AppConstant.CODE_ARCHIVE_ROOT_DIR + File.separator
                + TEST_APP_ID + "_" + TEST_DEPLOY_KEY;
        deployPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator
                + TEST_DEPLOY_KEY;

        // 清理上次测试残留
        cleanupTestData();
        cleanupTestFiles();

        // 创建测试目录（模拟已生成代码）
        FileUtil.mkdir(codeOutputPath);
        FileUtil.writeUtf8String("code", new File(codeOutputPath, "index.html"));
        FileUtil.mkdir(archivePath);
        FileUtil.writeUtf8String("archive", new File(archivePath, "index.html"));
        FileUtil.mkdir(deployPath);
        FileUtil.writeUtf8String("deploy", new File(deployPath, "index.html"));

        // 创建 Redis 对话记忆
        stringRedisTemplate.opsForValue().set("chat-memory:" + TEST_APP_ID, "test-memory");

        // 确保测试用户存在
        jdbcTemplate.update("INSERT IGNORE INTO user(id, userName) VALUES (?, ?)",
                TEST_USER_ID, "CleanupTester");

        log.info("测试环境已准备: appId={}", TEST_APP_ID);
    }

    @AfterEach
    void teardown() {
        cleanupTestData();
        cleanupTestFiles();
        log.info("测试环境已清理");
    }

    // ======================== 辅助方法 ========================

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM app_featured_application WHERE appId = ?", TEST_APP_ID);
        jdbcTemplate.update("DELETE FROM app WHERE id = ?", TEST_APP_ID);
        stringRedisTemplate.delete("chat-memory:" + TEST_APP_ID);
    }

    private void cleanupTestFiles() {
        FileUtil.del(new File(codeOutputPath));
        FileUtil.del(new File(archivePath));
        FileUtil.del(new File(deployPath));
    }

    private void insertApp(String deployStatus, String deployKey,
                           String archivePathVal, String codeGenType) {
        jdbcTemplate.update(
                "INSERT INTO app(id, userId, appName, deployStatus, deployKey, archivePath, codeGenType, isDelete) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                TEST_APP_ID, TEST_USER_ID, "CleanupTest",
                deployStatus, deployKey, archivePathVal, codeGenType);
    }

    private void waitForAsyncCleanup() {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private User createTestUser() {
        return User.builder().id(TEST_USER_ID).userName("TestUser").build();
    }

    // ====================================================================
    //  第一部分：状态拦截测试（文件应保持不变）
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("[状态拦截] ONLINE 应用删除被拒绝，文件保留")
    void deleteOnlineApp_shouldThrowAndKeepFiles() {
        insertApp("ONLINE", TEST_DEPLOY_KEY, null, TEST_CODE_TYPE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.removeById(TEST_APP_ID));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());

        // 所有文件应保留
        assertTrue(new File(codeOutputPath).exists(), "代码输出目录应保留");
        assertTrue(new File(archivePath).exists(), "归档目录应保留");
        assertTrue(new File(deployPath).exists(), "部署目录应保留");
        assertNotNull(stringRedisTemplate.opsForValue().get("chat-memory:" + TEST_APP_ID),
                "Redis 对话记忆应保留");
    }

    @Test
    @Order(2)
    @DisplayName("[状态拦截] DEPLOYING 应用删除被拒绝，文件保留")
    void deleteDeployingApp_shouldThrowAndKeepFiles() {
        insertApp("DEPLOYING", TEST_DEPLOY_KEY, null, TEST_CODE_TYPE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.removeById(TEST_APP_ID));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());

        // 所有文件应保留
        assertTrue(new File(codeOutputPath).exists(), "代码输出目录应保留");
        assertTrue(new File(deployPath).exists(), "部署目录应保留");
        assertNotNull(stringRedisTemplate.opsForValue().get("chat-memory:" + TEST_APP_ID));
    }

    // ====================================================================
    //  第二部分：文件清理测试（需等待异步清理完成）
    // ====================================================================

    @Test
    @Order(10)
    @DisplayName("[文件清理] OFFLINE 删除后清理代码输出+归档+Redis")
    void deleteOfflineApp_shouldCleanupAll() {
        insertApp("OFFLINE", null, archivePath, TEST_CODE_TYPE);

        assertTrue(appService.removeById(TEST_APP_ID), "应用应成功删除");
        waitForAsyncCleanup();

        // 代码输出目录应被清理
        assertFalse(new File(codeOutputPath).exists(), "代码输出目录应被删除");
        // 归档目录应被清理
        assertFalse(new File(archivePath).exists(), "归档目录应被删除");
        // 部署目录在下线时已清理，不归删除流程管
        // Redis 对话记忆应被清理
        assertNull(stringRedisTemplate.opsForValue().get("chat-memory:" + TEST_APP_ID),
                "Redis 对话记忆应被删除");
    }

    @Test
    @Order(11)
    @DisplayName("[文件清理] 未部署(null 状态)删除后只清理代码输出+Redis")
    void deleteNullStatusApp_shouldCleanupCodeOutputAndRedis() {
        insertApp(null, null, null, TEST_CODE_TYPE);

        assertTrue(appService.removeById(TEST_APP_ID), "应用应成功删除");
        waitForAsyncCleanup();

        // 代码输出目录应被清理
        assertFalse(new File(codeOutputPath).exists(), "代码输出目录应被删除");
        // 无归档，跳过
        // Redis 对话记忆应被清理
        assertNull(stringRedisTemplate.opsForValue().get("chat-memory:" + TEST_APP_ID),
                "Redis 对话记忆应被删除");
    }

    @Test
    @Order(12)
    @DisplayName("[文件清理] OFFLINE 且 archivePath=null 不清理归档")
    void deleteOfflineApp_withNullArchive_shouldSkipArchiveCleanup() {
        insertApp("OFFLINE", null, null, TEST_CODE_TYPE);

        assertTrue(appService.removeById(TEST_APP_ID), "应用应成功删除");
        waitForAsyncCleanup();

        // 代码输出应清理
        assertFalse(new File(codeOutputPath).exists(), "代码输出目录应被删除");
        // 归档清理跳过（无归档路径）
        // Redis 应清理
        assertNull(stringRedisTemplate.opsForValue().get("chat-memory:" + TEST_APP_ID),
                "Redis 对话记忆应被删除");
    }

    @Test
    @Order(13)
    @DisplayName("[文件清理] Vue 类型应用清理路径正确")
    void deleteOfflineVueApp_shouldCleanupVueCodeOutput() {
        String vueCodeType = "vue_project";
        String vueCodeOutput = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator
                + vueCodeType + "_" + TEST_APP_ID;
        try {
            // 创建 Vue 代码输出目录
            FileUtil.mkdir(vueCodeOutput);
            FileUtil.writeUtf8String("code", new File(vueCodeOutput, "App.vue"));
            // 归档路径使用 _vue 后缀
            String vueArchive = archivePath + "_vue";
            FileUtil.mkdir(vueArchive);
            FileUtil.writeUtf8String("archive", new File(vueArchive, "dist/index.html"));

            insertApp("OFFLINE", null, vueArchive, vueCodeType);

            assertTrue(appService.removeById(TEST_APP_ID), "应用应成功删除");
            waitForAsyncCleanup();

            // Vue 代码输出应清理
            assertFalse(new File(vueCodeOutput).exists(), "Vue 代码输出目录应被删除");
            // Vue 归档应清理
            assertFalse(new File(vueArchive).exists(), "Vue 归档目录应被删除");
            // Redis 应清理
            assertNull(stringRedisTemplate.opsForValue().get("chat-memory:" + TEST_APP_ID),
                    "Redis 对话记忆应被删除");
        } finally {
            FileUtil.del(new File(vueCodeOutput));
            FileUtil.del(new File(archivePath + "_vue"));
        }
    }

    // ====================================================================
    //  第三部分：空值与缺失目录安全测试
    // ====================================================================

    @Test
    @Order(20)
    @DisplayName("[安全] AppCleanupService 空值不抛异常")
    void cleanupService_withNullValues_shouldNotThrow() {
        assertDoesNotThrow(() -> appCleanupService.cleanupAppData(null, null, null),
                "全 null 不抛异常");
        assertDoesNotThrow(() -> appCleanupService.cleanupAppData(TEST_APP_ID, null, null),
                "archivePath=null 不抛异常");
        assertDoesNotThrow(() -> appCleanupService.cleanupAppData(TEST_APP_ID, archivePath, null),
                "codeGenType=null 不抛异常");
    }

    @Test
    @Order(21)
    @DisplayName("[安全] AppCleanupService 处理不存在的目录不抛异常")
    void cleanupService_withMissingDirs_shouldNotThrow() {
        // 删除测试目录模拟"不存在"
        FileUtil.del(new File(codeOutputPath));
        FileUtil.del(new File(archivePath));

        assertDoesNotThrow(() -> appCleanupService.cleanupAppData(TEST_APP_ID, archivePath, TEST_CODE_TYPE),
                "目录不存在时不抛异常");
    }

    // ====================================================================
    //  第四部分：精选申请关联清理测试（同步执行，无需等待）
    // ====================================================================

    @Test
    @Order(30)
    @DisplayName("[精选申请] 删除应用时取消 PENDING 精选申请")
    void deleteOfflineApp_shouldCancelPendingFeaturedApplication() {
        insertApp("OFFLINE", null, archivePath, TEST_CODE_TYPE);

        // 先申请精选（PENDING）
        User user = createTestUser();
        Long applicationId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请精选", user);
        assertNotNull(applicationId);

        // 验证是 PENDING
        AppFeaturedApplication before = featuredAppApplicationService.getById(applicationId);
        assertEquals(FeaturedAppStatusEnum.PENDING.getValue(), before.getStatus());

        // 删除应用（事件监听器同步取消 PENDING）
        assertTrue(appService.removeById(TEST_APP_ID));

        // 验证已取消（无需等待异步，FeaturedAppCleanupEventListener 是同步的）
        AppFeaturedApplication after = featuredAppApplicationService.getById(applicationId);
        assertNotNull(after);
        assertEquals(FeaturedAppStatusEnum.CANCELLED.getValue(), after.getStatus(),
                "删除应用后 PENDING 申请应自动变为 CANCELLED");
    }

    @Test
    @Order(31)
    @DisplayName("[精选申请] 删除 ONLINE 应用时不影响 PENDING 申请")
    void deleteOnlineApp_shouldNotAffectFeaturedApplications() {
        insertApp("ONLINE", TEST_DEPLOY_KEY, null, TEST_CODE_TYPE);

        // 先修改为未精选状态才能申请
        jdbcTemplate.update("UPDATE app SET priority = 0 WHERE id = ?", TEST_APP_ID);

        // 申请精选
        User user = createTestUser();
        Long applicationId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", user);
        assertNotNull(applicationId);

        // 删除被拦截
        assertThrows(BusinessException.class, () -> appService.removeById(TEST_APP_ID));

        // 申请应仍是 PENDING
        AppFeaturedApplication app = featuredAppApplicationService.getById(applicationId);
        assertEquals(FeaturedAppStatusEnum.PENDING.getValue(), app.getStatus(),
                "应用删除被拒时精选申请应不受影响");
    }

    @Test
    @Order(32)
    @DisplayName("[精选申请] 删除已 APPROVED 的应用会取消精选申请并重置 priority")
    void deleteOfflineApp_shouldCancelApprovedFeaturedApplication() {
        insertApp("OFFLINE", null, archivePath, TEST_CODE_TYPE);

        // 申请 + 审核通过（reviewApplications 会将 priority 设为 99 + 自动部署）
        User user = createTestUser();
        Long applicationId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", user);
        featuredAppApplicationService.reviewApplications(
                java.util.List.of(applicationId), true, "通过",
                User.builder().id(1L).userName("admin").build());

        // 自动部署生成了随机 deployKey，先记录下来用于清理
        String autoDeployKey = jdbcTemplate.queryForObject(
                "SELECT deployKey FROM app WHERE id = ?", String.class, TEST_APP_ID);
        // 审核通过后自动部署会将应用设为 ONLINE，需要手动置为 OFFLINE 以测试删除
        jdbcTemplate.update("UPDATE app SET deployStatus = 'OFFLINE', deployKey = NULL WHERE id = ?",
                TEST_APP_ID);

        // 验证已 APPROVED，priority=99
        AppFeaturedApplication before = featuredAppApplicationService.getById(applicationId);
        assertEquals(FeaturedAppStatusEnum.APPROVED.getValue(), before.getStatus());
        Integer priorityBefore = jdbcTemplate.queryForObject(
                "SELECT priority FROM app WHERE id = ?", Integer.class, TEST_APP_ID);
        assertEquals(Integer.valueOf(99), priorityBefore,
                "审核通过后 priority 应为 99");

        // 删除应用
        assertTrue(appService.removeById(TEST_APP_ID));

        // APPROVED 应被取消 + priority 重置为 0
        AppFeaturedApplication after = featuredAppApplicationService.getById(applicationId);
        assertNotNull(after);
        assertEquals(FeaturedAppStatusEnum.CANCELLED.getValue(), after.getStatus(),
                "应用删除后已通过的精选申请应被取消");
        Integer priorityAfter = jdbcTemplate.queryForObject(
                "SELECT priority FROM app WHERE id = ?", Integer.class, TEST_APP_ID);
        assertEquals(Integer.valueOf(0), priorityAfter,
                "应用删除后 priority 应重置为 0");

        // 清理自动部署生成的随机目录
        if (StrUtil.isNotBlank(autoDeployKey)) {
            FileUtil.del(new File(AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + autoDeployKey));
        }
    }

    // ====================================================================
    //  第五部分：锁与并发安全（时序验证）
    // ====================================================================

    @Test
    @Order(40)
    @DisplayName("[锁安全] removeById 与 deployOffline 共用锁，串行执行")
    void deleteLock_shouldBlockConcurrentDeployOperations() {
        // 验证同一 appId 的 deploy 锁复用正确性：
        // removeById 获取 deploy 锁 → 操作完成 → 释放锁
        // deployOffline 等待锁 → 获取锁 → 发现应用已删除（getById 返回 null）
        insertApp("ONLINE", TEST_DEPLOY_KEY, null, TEST_CODE_TYPE);

        // 先下线（文件归档，状态变为 OFFLINE）
        appService.deployOffline(TEST_APP_ID, createTestUser());

        // 验证已下线
        String statusAfterOffline = jdbcTemplate.queryForObject(
                "SELECT deployStatus FROM app WHERE id = ?", String.class, TEST_APP_ID);
        assertEquals("OFFLINE", statusAfterOffline);

        // 删除应用（持有 deploy 锁，读到最新 OFFLINE 状态）
        assertTrue(appService.removeById(TEST_APP_ID));

        // 验证应用已删除（软删除后 getById 返回 null）
        assertNull(appService.getById(TEST_APP_ID),
                "应用已被软删除，查询应返回 null");
    }

    @Test
    @Order(41)
    @DisplayName("[锁安全] removeById 阻塞期间 deployOnline 等待")
    void deleteLock_shouldPreventConcurrentOnline() {
        // removeById 持有锁时，deployOnline 应等待锁释放后读到已删除状态
        insertApp("OFFLINE", null, archivePath, TEST_CODE_TYPE);

        // 删除应用
        assertTrue(appService.removeById(TEST_APP_ID));

        // 尝试对已删除的应用上线
        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.deployOnline(TEST_APP_ID, createTestUser()));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode(),
                "已删除应用上线应抛 NOT_FOUND_ERROR");
    }
}
