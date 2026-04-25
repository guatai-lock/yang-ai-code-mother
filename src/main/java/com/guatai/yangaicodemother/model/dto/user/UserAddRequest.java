package com.guatai.yangaicodemother.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * ClassName: user
 * Package: com.guatai.yangaicodemother.model.dto.user
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/22 下午4:09
 * @Version 1.0
 */
@Data
public class UserAddRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色: user, admin
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}

