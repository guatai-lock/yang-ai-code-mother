package com.guatai.yangaicodemother.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 虚拟线程执行器配置
 *
 * <p>提供共享的虚拟线程 ExecutorService，用于执行 I/O 密集型异步任务
 * （如截图生成、文件构建等），替代原始的 {@code Thread.ofVirtual().start()} 调用方式。</p>
 *
 * <p>使用 {@link Executors#newVirtualThreadPerTaskExecutor()} 而非线程池，
 * 因为虚拟线程本身就是轻量级的，无需池化。</p>
 */
@Configuration
@Slf4j
public class VirtualThreadConfig {

    /**
     * 虚拟线程执行器（每任务创建新虚拟线程）
     *
     * <ul>
     *   <li>底层由 ForkJoinPool 承载，虚拟线程在阻塞操作时自动让出载体线程</li>
     *   <li>作为 Spring Bean 管理，应用关闭时会自动调用 {@link ExecutorService#shutdown()}</li>
     *   <li>注入时使用 {@code @Qualifier("virtualThreadExecutor")} 限定</li>
     * </ul>
     */
    @Bean("virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        log.info("初始化虚拟线程执行器 (VirtualThreadPerTaskExecutor)");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
