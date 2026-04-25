package com.guatai.yangaicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * ClassName: mutil
 * Package: com.guatai.yangaicodemother.ai.model
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/24 下午9:42
 * @Version 1.0
 */
@Description("生成多个代码文件的结果")
@Data
public class MultiFileCodeResult {

    @Description("HTML代码")
    private String htmlCode;

    @Description("CSS代码")
    private String cssCode;

    @Description("JS代码")
    private String jsCode;

    @Description("生成代码的描述")
    private String description;
}


