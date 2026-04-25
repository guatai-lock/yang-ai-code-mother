package com.guatai.yangaicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * ClassName: htmlcode
 * Package: com.guatai.yangaicodemother.ai.model
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/24 下午9:42
 * @Version 1.0
 */
@Description("生成 HTML 代码文件的结果")
@Data
public class HtmlCodeResult {

    @Description("HTML代码")
    private String htmlCode;

    @Description("生成代码的描述")
    private String description;
}


