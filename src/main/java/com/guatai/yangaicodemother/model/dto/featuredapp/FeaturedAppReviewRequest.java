package com.guatai.yangaicodemother.model.dto.featuredapp;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 精选申请审核请求 (支持单个和批量)
 */
@Data
public class FeaturedAppReviewRequest implements Serializable {

    /**
     * 申请记录id列表 (单个审核时传1个,批量审核时传多个)
     */
    private List<Long> applicationIds;

    /**
     * 是否通过 (true=通过, false=拒绝)
     */
    private Boolean approved;

    /**
     * 审核意见
     */
    private String reviewComment;

    private static final long serialVersionUID = 1L;
}
