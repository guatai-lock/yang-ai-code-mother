package com.guatai.yangaicodemother.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * ClassName: user
 * Package: com.guatai.yangaicodemother.model.dto.user
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/22 下午4:12
 * @Version 1.0
 */
@Data
public class UserUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}

