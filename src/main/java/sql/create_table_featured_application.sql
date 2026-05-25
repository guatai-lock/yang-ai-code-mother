-- 精选申请表
-- 创建日期：2026-05-25
-- 说明：支持用户申请精选应用、管理员审核功能

USE yang_ai_code_mother;

-- 创建精选申请表
CREATE TABLE app_featured_application (
    id BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    appId BIGINT NOT NULL COMMENT '应用id',
    userId BIGINT NOT NULL COMMENT '申请人id',
    reason VARCHAR(1024) COMMENT '申请理由',
    status VARCHAR(32) DEFAULT 'PENDING' NOT NULL COMMENT '审核状态：PENDING(待审核)/APPROVED(已通过)/REJECTED(已拒绝)/CANCELLED(已撤销)',
    reviewComment VARCHAR(1024) COMMENT '审核意见',
    reviewerId BIGINT COMMENT '审核人id',
    reviewTime DATETIME COMMENT '审核时间',
    createTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete TINYINT DEFAULT 0 NOT NULL COMMENT '是否删除',
    
    INDEX idx_appId (appId),
    INDEX idx_userId (userId),
    INDEX idx_status (status),
    INDEX idx_createTime (createTime)
) COMMENT '应用精选申请表' COLLATE = utf8mb4_unicode_ci;
