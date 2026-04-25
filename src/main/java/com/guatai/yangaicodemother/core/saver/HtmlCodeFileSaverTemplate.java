package com.guatai.yangaicodemother.core.saver;

/**
 * ClassName: ac
 * Package: com.guatai.yangaicodemother.core.saver
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/25 下午3:10
 * @Version 1.0
 */

import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.ai.model.HtmlCodeResult;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;

/**
 * HTML代码文件保存器
 *
 * @author yupi
 */
public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.HTML;
    }
    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        // 保存 HTML 文件
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
    }

    @Override
    protected void validateInput(HtmlCodeResult result) {
        super.validateInput(result);
        // HTML 代码不能为空
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
    }
}

