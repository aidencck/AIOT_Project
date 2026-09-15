package com.aiot.rule.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 任务线程池配置。
 *
 * <p>自定义 {@code aiLlmExecutor} 后，Spring Boot 的 {@code applicationTaskExecutor}
 * 自动装配会因 {@code @ConditionalOnMissingBean(Executor.class)} 退避，故此处显式
 * 声明 {@code applicationTaskExecutor} 以保持既有异步任务（规则草案生成等）可用。</p>
 */
@Configuration
public class AiLlmExecutorConfig {

    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor(TaskExecutorBuilder builder) {
        return builder.build();
    }

    /**
     * LLM 后台推理专用有界线程池。
     *
     * <p>异步化后请求线程立即返回确定性兜底，LLM 推理在线程池内受限并行回填。
     * 通过限制并发与队列容量，避免在 CPU 推理 + 内存受限环境下同时堆积多个
     * llama-server 请求导致 OOM（llama-server killed）。</p>
     */
    @Bean(name = "aiLlmExecutor")
    public ThreadPoolTaskExecutor aiLlmExecutor(
            @Value("${aiot.ai.diagnosis.llm-concurrency:1}") int concurrency,
            @Value("${aiot.ai.diagnosis.llm-queue-capacity:64}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-llm-refine-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
