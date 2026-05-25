package com.guatai.yangaicodemother.model.dto.featuredapp;

import lombok.Data;

import java.io.Serializable;

/**
 * 精选申请更新请求 (撤销等操作)
 */
@Data
public class FeaturedAppUpdateRequest implements Serializable {

    /**
     * 申请记录id
     */
    private Long applicationId;

    /**
     * 操作类型：CANCEL(撤销申请)
     */
    private String action;

    private static final long serialVersionUID = 1L;
}
