package com.guatai.yangaicodemother.rag.splitter;

import com.guatai.yangaicodemother.rag.model.CodeDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 多文件项目拆分器
 *
 * <p>每个源文件保留为一个独立的 chunk。
 * 适用于 {@code MULTI_FILE} 类型的项目。</p>
 */
@Slf4j
@Component
public class MultiSplitter implements CodeSplitter {

    @Override
    public List<CodeDocument> chunk(String filePath, String content, String appId, String projectType) {
        List<CodeDocument> chunks = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return chunks;
        }

        // 每个文件保留为一个 chunk
        chunks.add(CodeDocument.builder()
                .appId(appId)
                .projectType(projectType)
                .fileName(filePath)
                .section("full")
                .content(content.trim())
                .build());

        log.debug("Multi 文件拆分完成: file={}", filePath);
        return chunks;
    }

    @Override
    public boolean supports(String filePath) {
        // MultiSplitter 作为兜底拆分器，支持所有文件
        return true;
    }
}
