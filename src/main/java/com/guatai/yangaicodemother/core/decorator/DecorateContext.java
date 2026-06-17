package com.guatai.yangaicodemother.core.decorator;

import java.util.List;

/**
 * 消息装饰上下文 — 封装装饰器执行所需的参数
 * <p>
 * 随着装饰器种类增加，可在此类中追加字段，不影响已有装饰器。
 *
 * @param appId      应用 ID（图片装饰器需要）
 * @param skillNames 启用的技能名称列表（技能装饰器需要）
 */
public record DecorateContext(Long appId, List<String> skillNames) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long appId;
        private List<String> skillNames;

        public Builder appId(Long appId) {
            this.appId = appId;
            return this;
        }

        public Builder skillNames(List<String> skillNames) {
            this.skillNames = skillNames;
            return this;
        }

        public DecorateContext build() {
            return new DecorateContext(appId, skillNames);
        }
    }
}
