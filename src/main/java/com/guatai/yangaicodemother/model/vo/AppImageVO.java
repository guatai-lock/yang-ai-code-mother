package com.guatai.yangaicodemother.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用图片资源 视图对象。
 */
@Data
public class AppImageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * COS访问URL
     */
    private String cosUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 图片描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
