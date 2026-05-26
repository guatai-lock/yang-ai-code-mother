package com.guatai.yangaicodemother.service;

/**
 * 应用数据清理服务
 * 
 * 设计原则：
 * 1. 高内聚低耦合：专注于文件系统清理
 * 2. 异步非阻塞：不阻塞用户请求
 * 3. 容错设计：每个清理步骤独立try-catch
 */
public interface AppCleanupService {
    
    /**
     * 清理应用相关的所有数据（异步执行）
     * 
     * 前置条件：应用必须处于 OFFLINE 状态
     * 
     * @param appId 应用ID
     * @param archivePath 归档路径（下线时保存）
     * @param codeGenType 代码生成类型
     */
    void cleanupAppData(Long appId, String archivePath, String codeGenType);
}
