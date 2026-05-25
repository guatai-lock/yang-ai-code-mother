package com.guatai.yangaicodemother.model.dto.featuredapp;

import lombok.Data;

import java.io.Serializable;

/**
 * 精选应用申请请求
 */
@Data
public class FeaturedAppApplyRequest implements Serializable {

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 申请理由
     */
    private String reason;

    private static final long serialVersionUID = 1L;
}
