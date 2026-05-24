package com.guatai.yangaicodemother.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用部署状态视图对象
 *
 * @Author yang-ai-code-mother
 * @Date 2026-05-24
 */
@Data
public class DeployStatusVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用 ID
     */
    private Long appId;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 部署状态：ONLINE/OFFLINE/DEPLOYING
     */
    private String deployStatus;

    /**
     * 部署状态文本
     */
    private String deployStatusText;

    /**
     * 部署标识
     */
    private String deployKey;

    /**
     * 部署访问 URL
     */
    private String deployedUrl;

    /**
     * 部署时间
     */
    private LocalDateTime deployedTime;

    /**
     * 归档目录路径
     */
    private String archivePath;

    /**
     * 最后构建时间（Vue 项目专用）
     */
    private LocalDateTime lastBuildTime;

    /**
     * 代码生成类型
     */
    private String codeGenType;
}
