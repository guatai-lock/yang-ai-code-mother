package com.guatai.yangaicodemother.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用 实体类。
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app")
public class App implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator,value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 应用名称
     */
    @Column("appName")
    private String appName;

    /**
     * 应用封面
     */
    private String cover;

    /**
     * 应用初始化的 prompt
     */
    @Column("initPrompt")
    private String initPrompt;

    /**
     * 代码生成类型（枚举）
     */
    @Column("codeGenType")
    private String codeGenType;

    /**
     * 部署标识
     */
    @Column("deployKey")
    private String deployKey;

    /**
     * 部署时间
     */
    @Column("deployedTime")
    private LocalDateTime deployedTime;

    /**
     * 部署状态：ONLINE/OFFLINE/DEPLOYING
     */
    @Column("deployStatus")
    private String deployStatus;

    /**
     * 归档目录路径（下线时保存）
     */
    @Column("archivePath")
    private String archivePath;

    /**
     * 最后构建时间（Vue 项目专用，用于判断缓存是否过期）
     */
    @Column("lastBuildTime")
    private LocalDateTime lastBuildTime;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 对话轮次
     */
    @Column("conversationRound")
    private Integer conversationRound;

    /**
     * 创建用户id
     */
    @Column("userId")
    private Long userId;

    /**
     * 是否公开对话过程（精选应用专用）
     */
    @Column("public_chat_history")
    private Boolean publicChatHistory;

    /**
     * 编辑时间
     */
    @Column("editTime")
    private LocalDateTime editTime;

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
