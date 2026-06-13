package com.guatai.yangaicodemother.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码文档 POJO
 *
 * <p>表示从精选应用中提取的一个代码片段（chunk），
 * 用于向量化存储和检索。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeDocument {

    /**
     * 来源应用 ID
     */
    private String appId;

    /**
     * 代码生成类型：html / vue / multi
     */
    private String projectType;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 代码区域标识（如 header / hero / section / footer / template / script / style）
     */
    private String section;

    /**
     * 中文描述（由路由模型生成，用于提升语义检索精度）
     */
    private String description;

    /**
     * 代码内容
     */
    private String content;
}
