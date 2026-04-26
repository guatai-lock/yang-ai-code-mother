package com.guatai.yangaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员更新应用请求（支持更新应用名称、应用封面、优先级）
 */
@Data
public class AppUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    private static final long serialVersionUID = 1L;
}

