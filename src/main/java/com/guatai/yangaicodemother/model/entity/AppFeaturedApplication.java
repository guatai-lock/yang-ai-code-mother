package com.guatai.yangaicodemother.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 精选申请 实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_featured_application")
public class AppFeaturedApplication implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 应用id
     */
    @Column("appId")
    private Long appId;

    /**
     * 申请人id
     */
    @Column("userId")
    private Long userId;

    /**
     * 申请理由
     */
    private String reason;

    /**
     * 是否公开对话过程
     */
    @Column("public_chat_history")
    private Boolean publicChatHistory;

    /**
     * 审核状态：PENDING(待审核)/APPROVED(已通过)/REJECTED(已拒绝)/CANCELLED(已撤销)
     */
    private String status;

    /**
     * 审核意见
     */
    @Column("reviewComment")
    private String reviewComment;

    /**
     * 审核人id
     */
    @Column("reviewerId")
    private Long reviewerId;

    /**
     * 审核时间
     */
    @Column("reviewTime")
    private LocalDateTime reviewTime;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
