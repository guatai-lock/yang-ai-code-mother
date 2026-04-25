package com.guatai.yangaicodemother.common;

import lombok.Data;

import java.io.Serializable;

/**
 * ClassName: dekete
 * Package: com.guatai.yangaicodemother.common
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/22 下午3:23
 * @Version 1.0
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}

