package com.guatai.yangaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 聊天生成代码请求 DTO
 * <p>
 * 封装代码生成所需的所有业务参数，替代原有的散参方法签名。
 * 图片上传在前端已通过独立接口完成，后端通过 {@code enrichWithImageContext} 自动注入。
 */
@Data
public class ChatToGenCodeRequest implements Serializable {

    /**
     * 应用 ID
     */
    private Long appId;

    /**
     * 用户消息
     */
    private String message;

    /**
     * 是否启用 RAG 知识库参考（默认 false）
     */
    private Boolean ragEnabled;

    /**
     * 启用的技能名称列表（可选）
     */
    private List<String> skillNames;

    private static final long serialVersionUID = 1L;
}
