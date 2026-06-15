package com.guatai.yangaicodemother.controller;

import com.guatai.yangaicodemother.common.BaseResponse;
import com.guatai.yangaicodemother.common.ResultUtils;
import com.guatai.yangaicodemother.config.SkillsLoader;
import com.guatai.yangaicodemother.model.entity.SkillMeta;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/skill")
public class SkillController {

    @Resource
    private SkillsLoader skillsLoader;

    @GetMapping("/list")
    public BaseResponse<List<SkillVO>> listSkills() {
        List<SkillMeta> allSkills = skillsLoader.getAllSkills();
        List<SkillVO> skillVOs = allSkills.stream()
                .map(skill -> new SkillVO(skill.getName(), skill.getDescription()))
                .toList();
        return ResultUtils.success(skillVOs);
    }

    public record SkillVO(String name, String description) {}
}
