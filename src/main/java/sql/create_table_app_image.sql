create table app_image
(
    id           bigint auto_increment comment 'id' primary key,
    appId        bigint                              not null comment '应用id',
    userId       bigint                              not null comment '用户id',
    originalName varchar(512)                        not null comment '原始文件名',
    cosUrl       varchar(1024)                       not null comment 'COS访问URL',
    fileSize     bigint                              not null comment '文件大小（字节）',
    fileType     varchar(32)                         not null comment '文件类型',
    description  varchar(512)                        null comment '图片描述',
    createTime   datetime default CURRENT_TIMESTAMP  not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP  not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                  not null comment '是否删除',
    INDEX idx_appId (appId),
    INDEX idx_userId (userId)
) comment '应用图片资源' collate = utf8mb4_unicode_ci;
