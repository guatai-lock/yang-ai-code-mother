-- 精选应用功能增强：新增 public_chat_history 字段
-- 用于让用户在申请精选时选择是否公开对话过程

-- app_featured_application 表：记录申请人是否同意公开对话过程
ALTER TABLE `app_featured_application`
    ADD COLUMN `public_chat_history` tinyint(1) DEFAULT 0 COMMENT '是否公开对话过程' AFTER `reason`;

-- app 表：精选审核通过时同步该字段，供公开查询使用
ALTER TABLE `app`
    ADD COLUMN `public_chat_history` tinyint(1) DEFAULT 0 COMMENT '是否公开对话过程（精选应用专用）' AFTER `priority`;
