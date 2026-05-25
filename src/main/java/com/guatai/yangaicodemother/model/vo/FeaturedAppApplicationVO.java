package com.guatai.yangaicodemother.model.vo;

import com.guatai.yangaicodemother.model.entity.App;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 精选申请视图对象
 */
@Data
public class FeaturedAppApplicationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 申请记录id
     */
    private Long id;

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 申请人id
     */
    private Long userId;

    /**
     * 申请人昵称
     */
    private String userName;

    /**
     * 申请理由
     */
    private String reason;

    /**
     * 审核状态：PENDING/APPROVED/REJECTED/CANCELLED
     */
    private String status;

    /**
     * 审核状态文本
     */
    private String statusText;

    /**
     * 审核意见
     */
    private String reviewComment;

    /**
     * 审核人id
     */
    private Long reviewerId;

    /**
     * 审核人昵称
     */
    private String reviewerName;

    /**
     * 审核时间
     */
    private LocalDateTime reviewTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
