package com.guatai.yangaicodemother.model.entity;

/**
 * 技能元数据 — 内存 POJO，非数据库实体
 */
public class SkillMeta {

    private String name;
    private String description;
    private String content;

    public SkillMeta() {
    }

    public SkillMeta(String name, String description, String content) {
        this.name = name;
        this.description = description;
        this.content = content;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
