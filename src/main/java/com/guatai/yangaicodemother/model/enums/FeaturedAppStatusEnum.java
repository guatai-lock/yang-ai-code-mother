package com.guatai.yangaicodemother.model.enums;

import lombok.Getter;

/**
 * 精选申请状态枚举
 */
@Getter
public enum FeaturedAppStatusEnum {

    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已拒绝"),
    CANCELLED("CANCELLED", "已撤销");

    private final String value;
    private final String text;

    FeaturedAppStatusEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据值获取枚举
     */
    public static FeaturedAppStatusEnum getEnumByValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (FeaturedAppStatusEnum statusEnum : values()) {
            if (statusEnum.value.equals(value)) {
                return statusEnum;
            }
        }
        return null;
    }
}
