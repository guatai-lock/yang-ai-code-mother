package com.guatai.yangaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * ClassName: a
 * Package: com.guatai.yangaicodemother.model.dto.app
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/26 下午9:45
 * @Version 1.0
 */
@Data
public class AppDeployRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    private static final long serialVersionUID = 1L;
}

