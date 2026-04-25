package com.guatai.yangaicodemother.model.dto.user;

import com.guatai.yangaicodemother.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * ClassName: a
 * Package: com.guatai.yangaicodemother.model.dto.user
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/22 下午4:13
 * @Version 1.0
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin/ban
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}

