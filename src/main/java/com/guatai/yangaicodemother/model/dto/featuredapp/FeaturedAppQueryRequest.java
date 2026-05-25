package com.guatai.yangaicodemother.model.dto.featuredapp;

import lombok.Data;

import java.io.Serializable;

/**
 * 精选申请查询请求
 */
@Data
public class FeaturedAppQueryRequest implements Serializable {

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 申请人id
     */
    private Long userId;

    /**
     * 审核状态：PENDING/APPROVED/REJECTED/CANCELLED
     */
    private String status;

    /**
     * 审核人id
     */
    private Long reviewerId;

    /**
     * 页码
     */
    private long pageNum = 1;

    /**
     * 页大小
     */
    private long pageSize = 10;

    private static final long serialVersionUID = 1L;
}
