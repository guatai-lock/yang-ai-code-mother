package com.guatai.yangaicodemother.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史公开视图对象（脱敏，不暴露 userId）
 * 用于精选应用对话过程公开查看
 */
@Data
public class ChatHistoryPublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息类型：USER/AI
     */
    private String messageType;

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
