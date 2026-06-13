package com.guatai.yangaicodemother.rag.splitter;

import com.guatai.yangaicodemother.rag.model.CodeDocument;

import java.util.List;

/**
 * 代码拆分器接口
 *
 * <p>将源代码文件按语义拆分为多个 {@link CodeDocument} 片段，
 * 每个片段附带元数据（projectType、section 等），用于向量化检索。</p>
 */
public interface CodeSplitter {

    /**
     * 拆分代码文件为多个文档片段
     *
     * @param filePath    文件路径（用于日志和元数据）
     * @param content     文件内容
     * @param appId       来源应用 ID
     * @param projectType 代码生成类型（html / vue / multi）
     * @return 拆分后的代码片段列表（可能为空）
     */
    List<CodeDocument> chunk(String filePath, String content, String appId, String projectType);

    /**
     * 当前拆分器是否支持该文件类型
     *
     * @param filePath 文件路径
     * @return true 如果支持
     */
    boolean supports(String filePath);
}
