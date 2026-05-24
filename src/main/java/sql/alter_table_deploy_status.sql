-- 应用表扩展：新增部署状态管理字段
-- 执行日期：2026-05-24
-- 说明：支持应用在线/下线控制、资源归档与恢复

USE yang_ai_code_mother;

-- 1. 新增部署状态字段
ALTER TABLE app 
ADD COLUMN deployStatus VARCHAR(32) DEFAULT 'OFFLINE' COMMENT '部署状态：ONLINE(在线)/OFFLINE(离线)/DEPLOYING(部署中)';

-- 2. 新增归档目录路径字段
ALTER TABLE app 
ADD COLUMN archivePath VARCHAR(512) COMMENT '归档目录路径（下线时保存）';

-- 3. 新增最后构建时间字段（Vue 项目专用）
ALTER TABLE app 
ADD COLUMN lastBuildTime DATETIME COMMENT '最后构建时间（用于判断缓存是否过期）';

-- 4. 移除 deployKey 唯一索引，改为普通索引
-- 原因：下线时需要设置 deployKey=NULL，唯一索引会导致冲突
ALTER TABLE app DROP INDEX uk_deployKey;
ALTER TABLE app ADD INDEX idx_deployKey (deployKey);

-- 5. 新增部署状态索引（提升查询性能）
ALTER TABLE app ADD INDEX idx_deployStatus (deployStatus);

-- 6. 初始化已有应用的部署状态
-- 如果 deployKey 不为空，说明已部署，设置为 ONLINE
UPDATE app SET deployStatus = 'ONLINE' WHERE deployKey IS NOT NULL AND deployKey != '';

-- 7. 如果 deployKey 为空，设置为 OFFLINE
UPDATE app SET deployStatus = 'OFFLINE' WHERE deployKey IS NULL OR deployKey = '';

-- 验证修改结果
SELECT 
    COLUMN_NAME, 
    COLUMN_TYPE, 
    IS_NULLABLE, 
    COLUMN_DEFAULT, 
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'yang_ai_code_mother'
  AND TABLE_NAME = 'app'
  AND COLUMN_NAME IN ('deployStatus', 'archivePath', 'lastBuildTime')
ORDER BY ORDINAL_POSITION;
