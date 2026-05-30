package com.guatai.yangaicodemother.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用图片资源 实体类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_image")
public class AppImage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 应用id
     */
    @Column("appId")
    private Long appId;

    /**
     * 用户id
     */
    @Column("userId")
    private Long userId;

    /**
     * 原始文件名
     */
    @Column("originalName")
    private String originalName;

    /**
     * COS访问URL
     */
    @Column("cosUrl")
    private String cosUrl;

    /**
     * 文件大小（字节）
     */
    @Column("fileSize")
    private Long fileSize;

    /**
     * 文件类型
     */
    @Column("fileType")
    private String fileType;

    /**
     * 图片描述
     */
    private String description;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
