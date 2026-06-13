package com.guatai.yangaicodemother.rag.splitter;

import com.guatai.yangaicodemother.rag.model.CodeDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vue SFC（单文件组件）拆分器
 *
 * <p>将 Vue 单文件组件按 {@code <template> / <script> / <style>} 拆分。
 * 小于等于 200 字符的文件整体保留。</p>
 */
@Slf4j
@Component
public class VueSplitter implements CodeSplitter {

    private static final int SMALL_FILE_THRESHOLD = 200;

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile(
            "<template[^>]*>(.*?)</template>", Pattern.DOTALL);
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<script[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern STYLE_PATTERN = Pattern.compile(
            "<style[^>]*>(.*?)</style>", Pattern.DOTALL);

    @Override
    public List<CodeDocument> chunk(String filePath, String content, String appId, String projectType) {
        List<CodeDocument> chunks = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return chunks;
        }

        // 小文件：整体保留
        if (content.length() <= SMALL_FILE_THRESHOLD) {
            chunks.add(createDoc(appId, projectType, filePath, "full", content));
            return chunks;
        }

        // 按 SFC 区域拆分
        extractSection(content, TEMPLATE_PATTERN, "template", filePath, appId, projectType, chunks);
        extractSection(content, SCRIPT_PATTERN, "script", filePath, appId, projectType, chunks);
        extractSection(content, STYLE_PATTERN, "style", filePath, appId, projectType, chunks);

        // 如果拆分后没有结果，整体保留
        if (chunks.isEmpty()) {
            chunks.add(createDoc(appId, projectType, filePath, "full", content));
        }

        log.debug("Vue SFC 拆分完成: file={}, chunks={}", filePath, chunks.size());
        return chunks;
    }

    @Override
    public boolean supports(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".vue");
    }

    private void extractSection(String content, Pattern pattern, String sectionType,
                                String filePath, String appId, String projectType,
                                List<CodeDocument> chunks) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String sectionContent = matcher.group().trim();
            if (!sectionContent.isEmpty()) {
                chunks.add(createDoc(appId, projectType, filePath, sectionType, sectionContent));
            }
        }
    }

    private CodeDocument createDoc(String appId, String projectType, String fileName, String section, String content) {
        return CodeDocument.builder()
                .appId(appId)
                .projectType(projectType)
                .fileName(fileName)
                .section(section)
                .content(content.trim())
                .build();
    }
}
