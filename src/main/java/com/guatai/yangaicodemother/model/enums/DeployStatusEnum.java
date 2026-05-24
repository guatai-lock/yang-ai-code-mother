package com.guatai.yangaicodemother.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 应用部署状态枚举
 *
 * @Author yang-ai-code-mother
 * @Date 2026-05-24
 */
@Getter
public enum DeployStatusEnum {

    ONLINE("在线", "ONLINE"),
    OFFLINE("离线", "OFFLINE"),
    DEPLOYING("部署中", "DEPLOYING");

    private final String text;
    private final String value;

    DeployStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static DeployStatusEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (DeployStatusEnum anEnum : DeployStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
