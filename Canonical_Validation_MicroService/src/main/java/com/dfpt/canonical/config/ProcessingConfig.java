package com.dfpt.canonical.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ProcessingConfig {

    @Bean("mqExecutor")
    public Executor mqExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(1);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("mq-");
        exec.initialize();
        return exec;
    }

    @Bean("s3Executor")
    public Executor s3Executor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(1);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("s3-");
        exec.initialize();
        return exec;
    }
}
