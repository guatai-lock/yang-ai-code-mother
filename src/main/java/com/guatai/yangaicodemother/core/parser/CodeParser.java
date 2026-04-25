package com.guatai.yangaicodemother.core.parser;

/**
 * ClassName: code
 * Package: com.guatai.yangaicodemother.core.parser
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/25 下午2:55
 * @Version 1.0
 */
/**
 * 代码解析器策略接口
 *
 * @author yupi
 */
public interface CodeParser<T> {

    /**
     * 解析代码内容
     *
     * @param codeContent 原始代码内容
     * @return 解析后的结果对象
     */
    T parseCode(String codeContent);
}

