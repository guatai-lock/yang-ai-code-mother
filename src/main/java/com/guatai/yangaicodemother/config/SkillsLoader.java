package com.guatai.yangaicodemother.config;

import com.guatai.yangaicodemother.model.entity.SkillMeta;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SkillsLoader {

    private static final String SKILLS_RESOURCE_PATTERN = "classpath:skills/*/SKILL.md";
    private static final String FRONTMATTER_DELIMITER = "---";

    private final Map<String, SkillMeta> skillsMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadSkills();
    }

    private void loadSkills() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(SKILLS_RESOURCE_PATTERN);
            if (resources.length == 0) {
                log.warn("未找到任何技能文件, 模式: {}", SKILLS_RESOURCE_PATTERN);
                return;
            }
            int successCount = 0;
            for (Resource resource : resources) {
                try {
                    SkillMeta skill = parseSkillFile(resource);
                    if (skill != null) {
                        skillsMap.put(skill.getName(), skill);
                        successCount++;
                    }
                } catch (Exception e) {
                    log.warn("技能文件解析失败, 跳过: {} - {}", resource.getFilename(), e.getMessage());
                }
            }
            log.info("技能加载完成, 共 {}/{} 个", successCount, resources.length);
        } catch (Exception e) {
            log.error("扫描技能文件失败: {}", e.getMessage(), e);
        }
    }

    private SkillMeta parseSkillFile(Resource resource) {
        StringBuilder rawContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rawContent.append(line).append("\n");
            }
        } catch (Exception e) {
            log.warn("读取技能文件失败: {} - {}", resource.getFilename(), e.getMessage());
            return null;
        }

        String fullContent = rawContent.toString();
        String name = null;
        String description = null;
        String body;

        if (fullContent.startsWith(FRONTMATTER_DELIMITER)) {
            int closingDelimiterIndex = fullContent.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
            if (closingDelimiterIndex > 0) {
                String frontmatter = fullContent.substring(FRONTMATTER_DELIMITER.length(), closingDelimiterIndex).trim();
                for (String frontLine : frontmatter.split("\n")) {
                    frontLine = frontLine.trim();
                    if (frontLine.startsWith("name:")) {
                        name = frontLine.substring("name:".length()).trim();
                    } else if (frontLine.startsWith("description:")) {
                        description = frontLine.substring("description:".length()).trim();
                    }
                }
                body = fullContent.substring(closingDelimiterIndex + FRONTMATTER_DELIMITER.length()).trim();
            } else {
                body = fullContent.substring(FRONTMATTER_DELIMITER.length()).trim();
            }
        } else {
            body = fullContent.trim();
        }

        if (name == null || name.isEmpty()) {
            name = resource.getFilename();
            if (name != null && name.endsWith(".md")) {
                name = name.substring(0, name.length() - ".md".length());
            }
        }
        if (description == null) description = "";
        if (body == null || body.isEmpty()) {
            log.warn("技能文件内容为空: {}", resource.getFilename());
            return null;
        }
        body = body.replaceAll("\\R", "\n");
        return new SkillMeta(name, description, body);
    }

    public SkillMeta getSkillByName(String name) { return skillsMap.get(name); }
    public List<SkillMeta> getAllSkills() { return List.copyOf(skillsMap.values()); }
    public int getSkillCount() { return skillsMap.size(); }
}
