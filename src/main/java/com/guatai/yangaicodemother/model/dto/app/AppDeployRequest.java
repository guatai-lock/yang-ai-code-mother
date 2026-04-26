package com.guatai.yangaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * ClassName: a
 * Package: com.guatai.yangaicodemother.model.dto.app
 * Description:
 *
 */
@Data
public class AppDeployRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    private static final long serialVersionUID = 1L;
}

