package com.guatai.yangaicodemother.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Web MVC 异步请求执行器配置
 * <p>
 * 解决 SSE 流式响应使用默认 SimpleAsyncTaskExecutor 的性能问题。
 * 配置有界线程池替代每次请求创建新线程的 SimpleAsyncTaskExecutor。
 * </p>
 */
@Configuration
public class WebAsyncConfig implements WebMvcConfigurer {

    /**
     * SSE 流式响应专用线程池
     */
    @Bean("sseTaskExecutor")
    public ThreadPoolTaskExecutor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("sse-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Resource(name = "sseTaskExecutor")
    private ThreadPoolTaskExecutor sseTaskExecutor;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(sseTaskExecutor);
        configurer.setDefaultTimeout(300000L); // 5 分钟超时
    }
}
