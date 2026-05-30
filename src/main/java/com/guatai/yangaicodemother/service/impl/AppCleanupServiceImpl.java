package com.guatai.yangaicodemother.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.service.AppCleanupService;
import com.guatai.yangaicodemother.service.AppImageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 应用数据清理服务实现
 * 
 * 清理范围（OFFLINE状态）：
 * 1. 代码输出目录：code_output/{type}_{appId}/
 *    - Vue项目：包含node_modules（50-200MB）
 *    - HTML/Multi-file：只有源文件
 * 2. 代码归档目录：code_archive/{archivePath}/
 *    - Vue项目：source/ + dist/
 *    - HTML/Multi-file：部署文件
 * 3. Redis对话记忆：按appId隔离
 * 
 * 注意：
 * - 不需要处理部署目录（下线时已删除）
 * - 直接删除整个目录，无需特殊处理子目录
 */
@Service
@Slf4j
public class AppCleanupServiceImpl implements AppCleanupService {
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AppImageService appImageService;
    
    @Override
    @Async("cleanupExecutor")
    public void cleanupAppData(Long appId, String archivePath, String codeGenType) {
        log.info("开始清理应用数据，appId: {}, codeGenType: {}, archivePath: {}", 
                appId, codeGenType, archivePath);
        
        int successCount = 0;
        int skipCount = 0;
        
        try {
            // 1. 删除代码输出目录（必删）
            if (cleanupCodeOutputDirectory(appId, codeGenType)) successCount++;
            else skipCount++;
            
            // 2. 删除代码归档目录（必删）
            if (cleanupCodeArchiveDirectory(archivePath)) successCount++;
            else skipCount++;
            
            // 3. 清理Redis对话记忆（必清）
            if (cleanupRedisChatMemory(appId)) successCount++;
            else skipCount++;

            // 4. 清理图片资源记录（必清）
            if (cleanupAppImages(appId)) successCount++;
            else skipCount++;

            log.info("应用数据清理完成，appId: {}, 成功: {}, 跳过: {}", appId, successCount, skipCount);
            
        } catch (Exception e) {
            log.error("应用数据清理异常，appId: {}", appId, e);
        }
    }
    
    /**
     * 删除代码输出目录
     * 
     * 目录格式：code_output/{type}_{appId}/
     * 示例：
     * - Vue: code_output/vue_project_123/ (包含node_modules，50-200MB)
     * - HTML: code_output/html_456/ (只有源文件)
     * - Multi-file: code_output/multi_file_789/ (只有源文件)
     */
    private boolean cleanupCodeOutputDirectory(Long appId, String codeGenType) {
        try {
            if (StrUtil.isBlank(codeGenType)) {
                log.warn("代码生成类型为空，跳过清理输出目录，appId: {}", appId);
                return false;
            }
            
            String dirName = codeGenType + "_" + appId;
            String dirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + dirName;

            return deleteDirectory(dirPath, "代码输出目录");
        } catch (Exception e) {
            log.error("删除代码输出目录失败，appId: {}", appId, e);
            return false;
        }
    }
    
    /**
     * 删除代码归档目录
     * 
     * 目录格式：直接使用数据库中的 archivePath
     * 示例：
     * - Vue: code_archive/123_Abc123_vue/ (包含source/和dist/)
     * - HTML/Multi-file: code_archive/456_Def789/ (直接是部署文件)
     */
    private boolean cleanupCodeArchiveDirectory(String archivePath) {
        try {
            if (StrUtil.isBlank(archivePath)) {
                log.warn("归档路径为空，跳过清理归档目录");
                return false;
            }
            
            return deleteDirectory(archivePath, "代码归档目录");
        } catch (Exception e) {
            log.error("删除代码归档目录失败，archivePath: {}", archivePath, e);
            return false;
        }
    }
    
    /**
     * 清理Redis对话记忆
     * 
     * LangChain4j 的 RedisChatMemoryStore 使用 appId 作为 key
     * 需要删除的 key 格式：chat-memory:{appId}
     */
    private boolean cleanupRedisChatMemory(Long appId) {
        try {
            // LangChain4j RedisChatMemoryStore 的 key 格式
            String key = "chat-memory:" + appId;
            
            // 删除 Redis 中的对话记忆
            Boolean deleted = stringRedisTemplate.delete(key);
            
            if (Boolean.TRUE.equals(deleted)) {
                log.info("Redis对话记忆已清理，appId: {}, key: {}", appId, key);
            } else {
                log.debug("Redis对话记忆不存在，appId: {}, key: {}", appId, key);
            }
            
            return true;
        } catch (Exception e) {
            log.error("清理Redis对话记忆失败，appId: {}", appId, e);
            return false;
        }
    }
    
    /**
     * 通用目录删除方法
     */
    private boolean deleteDirectory(String path, String description) {
        try {
            File dir = new File(path);
            if (dir.exists()) {
                FileUtil.del(dir);
                log.info("{}已删除: {}", description, path);
                return true;
            } else {
                log.debug("{}不存在，跳过: {}", description, path);
                return false;
            }
        } catch (Exception e) {
            log.error("删除{}失败: {}", description, path, e);
            return false;
        }
    }

    /**
     * 清理应用图片资源记录
     */
    private boolean cleanupAppImages(Long appId) {
        try {
            appImageService.deleteByAppId(appId);
            log.info("应用图片资源记录已清理，appId: {}", appId);
            return true;
        } catch (Exception e) {
            log.error("清理应用图片资源记录失败，appId: {}", appId, e);
            return false;
        }
    }
}

