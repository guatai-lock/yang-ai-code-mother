package com.guatai.yangaicodemother.core.strategy;

import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 代码生成策略注册表
 * <p>
 * 自动收集 Spring 容器中所有 {@link CodeGenStrategy} Bean，
 * 按 {@link CodeGenTypeEnum} 建立映射，提供 O(1) 查找。
 * 新增策略实现后自动注册，无需修改本类 — 符合开闭原则。
 */
@Slf4j
@Component
public class CodeGenStrategyRegistry {

    private final Map<CodeGenTypeEnum, CodeGenStrategy> strategyMap;

    /**
     * 构造时自动收集所有策略 Bean
     */
    public CodeGenStrategyRegistry(List<CodeGenStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toConcurrentMap(
                        CodeGenStrategy::getType,
                        Function.identity(),
                        (existing, replacement) -> {
                            log.warn("检测到重复的策略类型: {}, 覆盖为: {}",
                                    existing.getType(), replacement.getClass().getSimpleName());
                            return replacement;
                        }
                ));
    }

    @PostConstruct
    public void init() {
        log.info("策略注册表初始化完成，已注册类型: {}", strategyMap.keySet());
    }

    /**
     * 根据生成类型获取策略
     *
     * @param type 代码生成类型
     * @return 对应的策略实现
     * @throws BusinessException 如果该类型无对应策略
     */
    public CodeGenStrategy getStrategy(CodeGenTypeEnum type) {
        if (type == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码生成类型不能为 null");
        }
        CodeGenStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + type + "，请检查是否有对应的 CodeGenStrategy 实现");
        }
        return strategy;
    }

    /**
     * 获取所有已注册的策略
     */
    public Map<CodeGenTypeEnum, CodeGenStrategy> getAllStrategies() {
        return new ConcurrentHashMap<>(strategyMap);
    }
}
