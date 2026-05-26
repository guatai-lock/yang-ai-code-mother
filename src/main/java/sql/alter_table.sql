-- 数据库变更脚本
-- 使用前请确认当前数据库版本，按需执行

-- V2: 新增应用对话轮次字段
ALTER TABLE app ADD COLUMN conversationRound int DEFAULT 0 NOT NULL COMMENT '对话轮次';
