package com.guatai.yangaicodemother.rag.splitter;

import com.guatai.yangaicodemother.rag.model.CodeDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 语义区域拆分器
 *
 * <p>按 HTML 语义标签（header / hero / section / footer）拆分代码片段。
 * 小于等于 200 字符的文件整体保留。</p>
 */
@Slf4j
@Component
public class HtmlSplitter implements CodeSplitter {

    private static final int SMALL_FILE_THRESHOLD = 200;

    // 按语义区域匹配：<header>、hero 类/ID 的 div、<section id="xxx">、<footer>
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "(<(header|footer|nav)[^>]*>.*?</\\2>)" +
                    "|(<div[^>]*class=\"[^\"]*hero[^\"]*\"[^>]*>.*?</div>)" +
                    "|(<section[^>]*>.*?</section>)" +
                    "|(<div[^>]*class=\"[^\"]*(?:features|content|main|banner)[^\"]*\"[^>]*>.*?</div>)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

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

        // 按语义区域拆分
        Matcher matcher = SECTION_PATTERN.matcher(content);
        int lastEnd = 0;
        int sectionIndex = 0;

        while (matcher.find()) {
            // 提取匹配区域前的文本（非结构化内容）
            if (matcher.start() > lastEnd) {
                String beforeText = content.substring(lastEnd, matcher.start()).trim();
                if (!beforeText.isEmpty()) {
                    chunks.add(createDoc(appId, projectType, filePath, "section_" + sectionIndex++, beforeText));
                }
            }

            // 匹配到的语义区域
            String matchedSection = matcher.group();
            String sectionType = extractSectionType(matcher);
            chunks.add(createDoc(appId, projectType, filePath, sectionType, matchedSection));
            lastEnd = matcher.end();
        }

        // 如果正则未匹配到任何区域，整体保留
        if (chunks.isEmpty()) {
            chunks.add(createDoc(appId, projectType, filePath, "full", content));
        } else {
            // 有匹配的区域时，捕获区域之间的剩余未匹配内容
            if (lastEnd < content.length()) {
                String remaining = content.substring(lastEnd).trim();
                if (!remaining.isEmpty()) {
                    chunks.add(createDoc(appId, projectType, filePath, "remaining", remaining));
                }
            }
        }

        log.debug("HTML 拆分完成: file={}, chunks={}", filePath, chunks.size());
        return chunks;
    }

    @Override
    public boolean supports(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".html");
    }

    private String extractSectionType(Matcher matcher) {
        if (matcher.group(2) != null) return matcher.group(2).toLowerCase(); // header / footer / nav
        if (matcher.group(3) != null) return "hero";       // div.hero
        if (matcher.group(4) != null) return "section";     // section
        if (matcher.group(5) != null) {                     // div.features / content / main / banner
            String cls = matcher.group(5).toLowerCase();
            if (cls.contains("features")) return "features";
            if (cls.contains("content")) return "content";
            if (cls.contains("main")) return "main";
            if (cls.contains("banner")) return "banner";
            return "section";
        }
        return "unknown";
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
