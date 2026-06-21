package com.guatai.yangaicodemother.core.pipeline.stage;

import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.core.pipeline.GenStage;
import com.guatai.yangaicodemother.core.pipeline.PipelineContext;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.exception.ThrowUtils;
import com.guatai.yangaicodemother.mapper.AppMapper;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import com.guatai.yangaicodemother.model.enums.DeployStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 参数校验 + 应用查询 + 权限校验 + 类型解析 Stage
 * <p>
 * 合并 4 个紧密耦合的职责（自然顺序不可打乱）：
 * <ol>
 *   <li>校验 appId 和 message 参数</li>
 *   <li>从数据库查询应用</li>
 *   <li>校验当前用户对应用的访问权限（仅创建者可访问）</li>
 *   <li>解析代码生成类型枚举</li>
 * </ol>
 * </p>
 * <p>
 * <b>额外职责</b>：快照应用当前状态（priority 与 deployStatus），
 * 供 {@link ContentReviewStage} 在 cleanup 中判断是否需要提交内容审核。
 * </p>
 */
@Slf4j
@Component
@Order(10)
public class ValidationStage implements GenStage {

    private final AppMapper appMapper;

    public ValidationStage(AppMapper appMapper) {
        this.appMapper = appMapper;
    }

    @Override
    public void execute(PipelineContext context) {
        // 1. 参数校验
        Long appId = context.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(context.getOriginalMessage()),
                ErrorCode.PARAMS_ERROR, "用户消息不能为空");

        // 2. 查询应用
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        context.setApp(app);

        // 3. 权限校验（仅创建者可访问）
        if (!app.getUserId().equals(context.getLoginUser().getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }

        // 4. 解析代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        context.setCodeGenTypeEnum(codeGenTypeEnum);

        log.debug("ValidationStage 完成: appId={}, type={}, userId={}",
                appId, codeGenTypeStr, context.getLoginUser().getId());
    }
}
