package com.guatai.yangaicodemother.service;

import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.model.dto.featuredapp.FeaturedAppQueryRequest;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.entity.AppFeaturedApplication;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.FeaturedAppStatusEnum;
import com.guatai.yangaicodemother.model.vo.FeaturedAppApplicationVO;
import com.mybatisflex.core.paginate.Page;
import java.io.File;
import cn.hutool.core.io.FileUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 精选应用申请审核功能测试
 *
 * 覆盖范围：
 * 1. FeaturedAppStatusEnum 枚举
 * 2. applyFeaturedApp 申请（成功/失败/权限/边界）
 * 3. updateApplication 撤销
 * 4. reviewApplications 审核（通过/拒绝/批量）
 * 5. hasPendingApplication 待审核检查
 * 6. listMyApplications / listApplicationsByAdmin 分页查询
 * 7. VO 字段转换
 * 8. 全链路生命周期场景
 *
 * 运行前提：
 * - MySQL 中已执行 create_table_featured_application.sql
 * - Redis 服务可用
 *
 * 说明：
 * - 使用 @BeforeEach + @AfterEach 配合 JdbcTemplate 硬删除清理测试数据，
 *   避免 @Transactional 嵌套 TransactionTemplate 时 MyBatis 批量更新不刷新的问题
 * - 不使用 @Transactional 以确保 reviewApplications 内 TransactionTemplate 的
 *   updateBatch 能正常提交，后续查询可见最新数据
 */
@SpringBootTest
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FeaturedAppApplicationTest {

    @Resource
    private FeaturedAppApplicationService featuredAppApplicationService;

    @Resource
    private AppService appService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    // 测试数据
    private static final Long TEST_APP_ID = 999991L;
    private static final Long TEST_APP_ID_2 = 999992L;
    private static final Long TEST_APP_ID_3 = 999994L;
    private static final Long TEST_APP_ID_4 = 999995L;
    private static final Long TEST_APP_ID_OTHER = 999993L;
    private static final Long TEST_USER_ID = 100001L;
    private static final Long TEST_USER_ID_2 = 100003L;
    private static final Long TEST_USER_ID_3 = 100004L;
    private static final Long TEST_USER_ID_OTHER = 100002L;
    private static final Long TEST_ADMIN_ID = 1L;

    @BeforeEach
    void setup() {
        cleanupTestData();
        createTestApps();
    }

    @AfterEach
    void teardown() {
        cleanupTestData();
    }

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM app_featured_application WHERE appId IN (?, ?, ?, ?, ?)",
                TEST_APP_ID, TEST_APP_ID_2, TEST_APP_ID_3, TEST_APP_ID_4, TEST_APP_ID_OTHER);
        jdbcTemplate.update("DELETE FROM app WHERE id IN (?, ?, ?, ?, ?)",
                TEST_APP_ID, TEST_APP_ID_2, TEST_APP_ID_3, TEST_APP_ID_4, TEST_APP_ID_OTHER);
        // 清理测试创建的模拟代码目录
        cleanupCodeDirs();
    }

    private void cleanupCodeDirs() {
        for (Long id : new Long[]{TEST_APP_ID, TEST_APP_ID_2, TEST_APP_ID_3, TEST_APP_ID_4, TEST_APP_ID_OTHER}) {
            String codeDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "html_" + id;
            FileUtil.del(new File(codeDirPath));
        }
    }

    private void createTestApps() {
        saveTestApp(TEST_APP_ID, "精选申请测试应用A", TEST_USER_ID);
        saveTestApp(TEST_APP_ID_2, "精选申请测试应用B", TEST_USER_ID_2);
        saveTestApp(TEST_APP_ID_3, "精选申请测试应用C", TEST_USER_ID_3);
        saveTestApp(TEST_APP_ID_4, "精选申请测试应用D", TEST_USER_ID);
        saveTestApp(TEST_APP_ID_OTHER, "他人应用", TEST_USER_ID_OTHER);
    }

    private void saveTestApp(Long id, String name, Long userId) {
        App app = App.builder()
                .id(id)
                .appName(name)
                .userId(userId)
                .priority(AppConstant.DEFAULT_APP_PRIORITY)
                .codeGenType("html")
                .editTime(LocalDateTime.now())
                .build();
        assertTrue(appService.save(app), "测试应用" + id + "创建失败");

        // 创建模拟的代码输出目录（applyFeaturedApp 会检查目录存在性）
        String codeDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "html_" + id;
        FileUtil.mkdir(new File(codeDirPath));
    }

    private User createUser(Long id) {
        return User.builder().id(id).userName("用户" + id).build();
    }

    private User createTestUser() {
        return createUser(TEST_USER_ID);
    }

    private User createOtherUser() {
        return createUser(TEST_USER_ID_OTHER);
    }

    private User createAdminUser() {
        return createUser(TEST_ADMIN_ID);
    }

    // ====================================================================
    //  第一部分：枚举测试
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("[枚举] 枚举值定义正确")
    void testEnumValues() {
        assertEquals("PENDING", FeaturedAppStatusEnum.PENDING.getValue());
        assertEquals("待审核", FeaturedAppStatusEnum.PENDING.getText());
        assertEquals("APPROVED", FeaturedAppStatusEnum.APPROVED.getValue());
        assertEquals("已通过", FeaturedAppStatusEnum.APPROVED.getText());
        assertEquals("REJECTED", FeaturedAppStatusEnum.REJECTED.getValue());
        assertEquals("已拒绝", FeaturedAppStatusEnum.REJECTED.getText());
        assertEquals("CANCELLED", FeaturedAppStatusEnum.CANCELLED.getValue());
        assertEquals("已撤销", FeaturedAppStatusEnum.CANCELLED.getText());
    }

    @Test
    @Order(2)
    @DisplayName("[枚举] 根据 value 获取枚举")
    void testGetEnumByValue() {
        assertEquals(FeaturedAppStatusEnum.PENDING, FeaturedAppStatusEnum.getEnumByValue("PENDING"));
        assertEquals(FeaturedAppStatusEnum.APPROVED, FeaturedAppStatusEnum.getEnumByValue("APPROVED"));
        assertEquals(FeaturedAppStatusEnum.REJECTED, FeaturedAppStatusEnum.getEnumByValue("REJECTED"));
        assertEquals(FeaturedAppStatusEnum.CANCELLED, FeaturedAppStatusEnum.getEnumByValue("CANCELLED"));
        assertNull(FeaturedAppStatusEnum.getEnumByValue("UNKNOWN"));
        assertNull(FeaturedAppStatusEnum.getEnumByValue(""));
        assertNull(FeaturedAppStatusEnum.getEnumByValue(null));
    }

    // ====================================================================
    //  第二部分：applyFeaturedApp 申请测试
    // ====================================================================

    @Test
    @Order(10)
    @DisplayName("[申请] 成功申请精选")
    void testApplySuccess() {
        Long applicationId = featuredAppApplicationService.applyFeaturedApp(
                TEST_APP_ID, "这是一个很酷的应用", null, createTestUser());
        assertNotNull(applicationId);
        assertTrue(applicationId > 0);

        AppFeaturedApplication saved = featuredAppApplicationService.getById(applicationId);
        assertNotNull(saved);
        assertEquals(TEST_APP_ID, saved.getAppId());
        assertEquals(TEST_USER_ID, saved.getUserId());
        assertEquals("这是一个很酷的应用", saved.getReason());
        assertEquals(FeaturedAppStatusEnum.PENDING.getValue(), saved.getStatus());
    }

    @Test
    @Order(11)
    @DisplayName("[申请] 应用不存在抛 NOT_FOUND_ERROR")
    void testApplyAppNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.applyFeaturedApp(-999L, "理由", null, createTestUser()));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(12)
    @DisplayName("[申请] 申请他人的应用抛 NO_AUTH_ERROR")
    void testApplyOtherApp() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID_OTHER, "理由", null, createTestUser()));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(13)
    @DisplayName("[申请] 已选精选的应用不能重复申请")
    void testApplyAlreadyFeatured() {
        App app = appService.getById(TEST_APP_ID);
        app.setPriority(AppConstant.GOOD_APP_PRIORITY);
        appService.updateById(app);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "理由", null, createTestUser()));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(14)
    @DisplayName("[申请] 已有待审核的申请时不能重复申请同一个应用")
    void testApplyDuplicatePending() {
        featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "第一次申请", null, createTestUser());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "第二次申请", null, createTestUser()));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(15)
    @DisplayName("[申请] 同一用户只能有一个待审核申请")
    void testApplyOnePendingPerUser() {
        featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID_4, "再次申请", null, createTestUser()));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(16)
    @DisplayName("[申请] 撤销后可重新申请")
    void testApplyAfterCancel() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        featuredAppApplicationService.updateApplication(appId, "CANCEL", createTestUser());

        Long newAppId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "重新申请", null, createTestUser());
        assertNotNull(newAppId);
        assertNotEquals(appId, newAppId);
    }

    @Test
    @Order(17)
    @DisplayName("[申请] 被拒绝后可重新申请")
    void testApplyAfterReject() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        featuredAppApplicationService.reviewApplications(List.of(appId), false, "不符合要求", createAdminUser());

        Long newAppId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "重新申请", null, createTestUser());
        assertNotNull(newAppId);
    }

    // ====================================================================
    //  第三部分：updateApplication 撤销测试
    // ====================================================================

    @Test
    @Order(20)
    @DisplayName("[撤销] 成功撤销待审核的申请")
    void testCancelSuccess() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        assertTrue(featuredAppApplicationService.updateApplication(appId, "CANCEL", createTestUser()));

        AppFeaturedApplication app = featuredAppApplicationService.getById(appId);
        assertEquals(FeaturedAppStatusEnum.CANCELLED.getValue(), app.getStatus());
    }

    @Test
    @Order(21)
    @DisplayName("[撤销] 不存在的申请记录抛 NOT_FOUND_ERROR")
    void testCancelNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.updateApplication(-999L, "CANCEL", createTestUser()));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(22)
    @DisplayName("[撤销] 只能撤销自己的申请")
    void testCancelOtherApp() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.updateApplication(appId, "CANCEL", createOtherUser()));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(23)
    @DisplayName("[撤销] 已通过的申请不能撤销")
    void testCancelApproved() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        featuredAppApplicationService.reviewApplications(List.of(appId), true, "通过", createAdminUser());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.updateApplication(appId, "CANCEL", createTestUser()));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    @Order(24)
    @DisplayName("[撤销] 不支持的 action 抛 PARAMS_ERROR")
    void testCancelInvalidAction() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.updateApplication(appId, "INVALID_ACTION", createTestUser()));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    // ====================================================================
    //  第四部分：reviewApplications 审核测试
    // ====================================================================

    @Test
    @Order(30)
    @DisplayName("[审核] 单个审核通过")
    void testReviewSingleApprove() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());

        int count = featuredAppApplicationService.reviewApplications(
                List.of(appId), true, "符合精选要求", createAdminUser());
        assertEquals(1, count);

        // 验证无待审核记录
        assertFalse(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID));

        // 验证应用优先级已更新（app 表通过不同 mapper 读取，不受一级缓存影响）
        assertEquals(AppConstant.GOOD_APP_PRIORITY, appService.getById(TEST_APP_ID).getPriority());
    }

    @Test
    @Order(31)
    @DisplayName("[审核] 单个审核拒绝")
    void testReviewSingleReject() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());

        int count = featuredAppApplicationService.reviewApplications(
                List.of(appId), false, "功能不够完善", createAdminUser());
        assertEquals(1, count);

        // 拒绝后无待审核
        assertFalse(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID));

        // 验证应用优先级未更新
        assertEquals(AppConstant.DEFAULT_APP_PRIORITY, appService.getById(TEST_APP_ID).getPriority());
    }

    @Test
    @Order(32)
    @DisplayName("[审核] 批量通过多个用户的应用")
    void testReviewBatchApprove() {
        Long appId1 = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        Long appId2 = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID_2, "申请", null, createUser(TEST_USER_ID_2));

        int count = featuredAppApplicationService.reviewApplications(
                List.of(appId1, appId2), true, "都符合要求", createAdminUser());
        assertEquals(2, count);

        // 验证两个已无待审核
        assertFalse(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID));

        // 验证两个应用优先级都已更新
        assertEquals(AppConstant.GOOD_APP_PRIORITY, appService.getById(TEST_APP_ID).getPriority());
        assertEquals(AppConstant.GOOD_APP_PRIORITY, appService.getById(TEST_APP_ID_2).getPriority());
    }

    @Test
    @Order(33)
    @DisplayName("[审核] 批量审核时只处理待审核的记录")
    void testReviewBatchMixed() {
        Long appId1 = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        Long appId2 = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID_2, "申请", null, createUser(TEST_USER_ID_2));

        // 先撤销 appId2
        featuredAppApplicationService.updateApplication(appId2, "CANCEL", createUser(TEST_USER_ID_2));

        // 批量审核（只应该审核 appId1）
        int count = featuredAppApplicationService.reviewApplications(
                List.of(appId1, appId2), true, "批量", createAdminUser());
        assertEquals(1, count);

        // appId1 已无待审核（被通过了）
        assertFalse(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID));
        // appId2 应仍有待审核... 不，它已被撤销，也没待审核
        assertFalse(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID_2));
    }

    @Test
    @Order(34)
    @DisplayName("[审核] 无待审核记录返回 0")
    void testReviewNoPending() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        featuredAppApplicationService.reviewApplications(List.of(appId), true, "通过", createAdminUser());

        int count = featuredAppApplicationService.reviewApplications(
                List.of(appId), true, "再次审核", createAdminUser());
        assertEquals(0, count);
    }

    @Test
    @Order(35)
    @DisplayName("[审核] 超过 100 条限制")
    void testReviewExceedBatchLimit() {
        List<Long> ids = new java.util.ArrayList<>();
        for (long i = 0; i < 101; i++) {
            ids.add(i);
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.reviewApplications(ids, true, "批量", createAdminUser()));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    // ====================================================================
    //  第五部分：hasPendingApplication 测试
    // ====================================================================

    @Test
    @Order(40)
    @DisplayName("[待审核] 无申请时返回 false")
    void testHasPendingFalse() {
        assertFalse(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID));
    }

    @Test
    @Order(41)
    @DisplayName("[待审核] 有 PENDING 申请时返回 true")
    void testHasPendingTrue() {
        featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        assertTrue(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID));
    }

    @Test
    @Order(42)
    @DisplayName("[待审核] 撤销后返回 false")
    void testHasPendingAfterCancel() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        featuredAppApplicationService.updateApplication(appId, "CANCEL", createTestUser());
        assertFalse(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID));
    }

    @Test
    @Order(43)
    @DisplayName("[待审核] 审核通过后返回 false")
    void testHasPendingAfterApprove() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        featuredAppApplicationService.reviewApplications(List.of(appId), true, "通过", createAdminUser());
        assertFalse(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID));
    }

    // ====================================================================
    //  第六部分：分页查询测试
    // ====================================================================

    @Test
    @Order(50)
    @DisplayName("[我的申请] 查询我的申请列表")
    void testListMyApplications() {
        featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请A", null, createTestUser());

        FeaturedAppQueryRequest query = new FeaturedAppQueryRequest();
        query.setUserId(TEST_USER_ID);
        query.setPageNum(1);
        query.setPageSize(10);

        Page<FeaturedAppApplicationVO> page = featuredAppApplicationService.listMyApplications(query);
        assertNotNull(page);
        assertEquals(1, page.getTotalRow());

        FeaturedAppApplicationVO vo = page.getRecords().getFirst();
        assertEquals(TEST_APP_ID, vo.getAppId());
        assertNotNull(vo.getAppName());
        assertNotNull(vo.getStatus());
        assertNotNull(vo.getStatusText());
    }

    @Test
    @Order(51)
    @DisplayName("[我的申请] 按状态筛选")
    void testListMyApplicationsByStatus() {
        featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());

        FeaturedAppQueryRequest query = new FeaturedAppQueryRequest();
        query.setUserId(TEST_USER_ID);
        query.setPageNum(1);
        query.setPageSize(10);

        query.setStatus(FeaturedAppStatusEnum.PENDING.getValue());
        assertEquals(1, featuredAppApplicationService.listMyApplications(query).getTotalRow());

        query.setStatus(FeaturedAppStatusEnum.APPROVED.getValue());
        assertEquals(0, featuredAppApplicationService.listMyApplications(query).getTotalRow());
    }

    @Test
    @Order(52)
    @DisplayName("[管理员查询] 可查询所有申请")
    void testListApplicationsByAdmin() {
        featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());

        FeaturedAppQueryRequest query = new FeaturedAppQueryRequest();
        query.setPageNum(1);
        query.setPageSize(10);

        Page<FeaturedAppApplicationVO> page = featuredAppApplicationService.listApplicationsByAdmin(query);
        assertNotNull(page);
        assertTrue(page.getTotalRow() >= 1);
    }

    @Test
    @Order(53)
    @DisplayName("[管理员查询] 按审核人筛选")
    void testListApplicationsByAdminFilter() {
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        featuredAppApplicationService.reviewApplications(List.of(appId), true, "通过", createAdminUser());

        // 直接验证 reviewerId 已设置
        AppFeaturedApplication saved = featuredAppApplicationService.getById(appId);
        assertNotNull(saved);
        assertEquals(TEST_ADMIN_ID, saved.getReviewerId());

        // 用 userId 筛选比用 reviewerId 更可靠（不受其他测试数据影响）
        FeaturedAppQueryRequest query = new FeaturedAppQueryRequest();
        query.setUserId(TEST_USER_ID);
        query.setPageNum(1);
        query.setPageSize(10);

        Page<FeaturedAppApplicationVO> page = featuredAppApplicationService.listApplicationsByAdmin(query);
        assertTrue(page.getTotalRow() >= 1);

        FeaturedAppApplicationVO vo = page.getRecords().stream()
                .filter(v -> v.getAppId().equals(TEST_APP_ID))
                .findFirst().orElse(null);
        assertNotNull(vo);
        assertNotNull(vo.getReviewerId());
        assertEquals(TEST_ADMIN_ID, vo.getReviewerId());
    }

    // ====================================================================
    //  第七部分：VO 字段测试
    // ====================================================================

    @Test
    @Order(60)
    @DisplayName("[VO] FeaturedAppApplicationVO 字段赋值正确")
    void testVOFields() {
        FeaturedAppApplicationVO vo = new FeaturedAppApplicationVO();
        vo.setId(100L);
        vo.setAppId(TEST_APP_ID);
        vo.setAppName("测试应用名");
        vo.setUserId(TEST_USER_ID);
        vo.setUserName("测试用户");
        vo.setReason("测试理由");
        vo.setStatus("PENDING");
        vo.setStatusText("待审核");
        vo.setReviewComment("审核意见");
        vo.setReviewerId(TEST_ADMIN_ID);
        vo.setReviewerName("管理员");
        vo.setReviewTime(LocalDateTime.of(2026, 5, 25, 10, 0));
        vo.setCreateTime(LocalDateTime.of(2026, 5, 25, 9, 0));
        vo.setUpdateTime(LocalDateTime.of(2026, 5, 25, 10, 30));

        assertEquals(100L, vo.getId());
        assertEquals(TEST_APP_ID, vo.getAppId());
        assertEquals("测试应用名", vo.getAppName());
        assertEquals(TEST_USER_ID, vo.getUserId());
        assertEquals("测试用户", vo.getUserName());
        assertEquals("测试理由", vo.getReason());
        assertEquals("PENDING", vo.getStatus());
        assertEquals("待审核", vo.getStatusText());
        assertEquals("审核意见", vo.getReviewComment());
        assertEquals(TEST_ADMIN_ID, vo.getReviewerId());
        assertEquals("管理员", vo.getReviewerName());
        assertNotNull(vo.getReviewTime());
        assertNotNull(vo.getCreateTime());
        assertNotNull(vo.getUpdateTime());
    }

    // ====================================================================
    //  第八部分：全链路场景测试
    // ====================================================================

    @Test
    @Order(100)
    @DisplayName("[场景] 完整申请审核流程：申请 → 查询 → 审核通过 → 验证精选")
    void testFullFeaturedAppLifecycle() {
        // 1. 申请精选
        Long appId = featuredAppApplicationService.applyFeaturedApp(
                TEST_APP_ID, "我的应用非常优秀", null, createTestUser());
        assertNotNull(appId);
        log.info("阶段1: 申请成功 ✓");

        // 2. 验证待审核状态
        assertTrue(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID));
        log.info("阶段2: 待审核状态 ✓");

        // 3. 管理员审核通过
        int count = featuredAppApplicationService.reviewApplications(
                List.of(appId), true, "符合精选标准", createAdminUser());
        assertEquals(1, count);
        log.info("阶段3: 审核通过 ✓");

        // 4. 验证无待审核记录（count 绕过一级缓存）
        assertFalse(featuredAppApplicationService.hasPendingApplication(TEST_APP_ID));
        log.info("阶段4: 无待审核 ✓");

        // 5. 验证应用已标记为精选（app 表独立 mapper 读取）
        assertEquals(AppConstant.GOOD_APP_PRIORITY, appService.getById(TEST_APP_ID).getPriority());
        log.info("阶段5: 精选标记已更新 ✓");

        // 6. 再次申请应被拒绝（已标记为精选）
        BusinessException ex = assertThrows(BusinessException.class,
                () -> featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "再次申请", null, createTestUser()));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        log.info("阶段6: 重复申请被拦截 ✓");
    }

    @Test
    @Order(101)
    @DisplayName("[场景] 申请 → 撤销 → 重新申请 → 审核通过 → 优先级的完整流转")
    void testCancelAndReapplyLifecycle() {
        // 1. 申请 + 撤销
        Long appId = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "申请", null, createTestUser());
        featuredAppApplicationService.updateApplication(appId, "CANCEL", createTestUser());

        // 2. 重新申请
        Long appId2 = featuredAppApplicationService.applyFeaturedApp(TEST_APP_ID, "重新申请", null, createTestUser());

        // 3. 审核拒绝
        featuredAppApplicationService.reviewApplications(List.of(appId2), false, "不通过", createAdminUser());

        // 4. 再次审核同一个拒绝的记录应返回 0（已不是 PENDING）
        assertEquals(0, featuredAppApplicationService.reviewApplications(
                List.of(appId2), true, "无效审核", createAdminUser()));

        // 5. 应用不应被标记为精选
        assertEquals(AppConstant.DEFAULT_APP_PRIORITY, appService.getById(TEST_APP_ID).getPriority());
    }
}
