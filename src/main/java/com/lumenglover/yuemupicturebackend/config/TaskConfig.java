package com.lumenglover.yuemupicturebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class TaskConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);  // 核心线程数
        executor.setMaxPoolSize(5);   // 最大线程数
        executor.setQueueCapacity(100); // 队列容量
        executor.setThreadNamePrefix("PictureScoreJob-"); // 线程名前缀
        executor.initialize();
        return executor;
    }

    /**
     * RAG 摘要专用线程池
     * 替代裸 new Thread().start()，由 Spring 统一管理生命周期，防止线程堆积泄漏
     */
    @Bean(name = "ragSummaryExecutor")
    public Executor ragSummaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("RagSummary-");
        executor.setDaemon(true); // 守护线程，不阻止 JVM 正常退出
        executor.setWaitForTasksToCompleteOnShutdown(false); // 关机时不等待摘要任务
        executor.initialize();
        return executor;
    }
}
