package com.guatai.yangaicodemother.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * ClassName: a
 * Package: com.guatai.yangaicodemother.model.dto.user
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/22 下午3:47
 * @Version 1.0
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;
}

