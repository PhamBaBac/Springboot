package com.bacpham.kanban_service.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Cấu hình Thread Pool và Bất đồng bộ (Async) cho toàn bộ ứng dụng.
 * - Kích hoạt @EnableAsync (trước đây EmailService có @Async nhưng không chạy vì thiếu annotation này).
 * - Sử dụng ThreadPoolTaskExecutor có giới hạn, tránh tạo thread vô hạn (OOM).
 * - CallerRunsPolicy: Nếu queue đầy, thread gọi sẽ tự xử lý, không bao giờ drop task.
 * - Graceful shutdown: Đợi các task hoàn tất khi server tắt.
 * - Logging chi tiết khi có exception trong Async method.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("kanban-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Exception in async method: {} with message: {}",
                    method.getName(), throwable.getMessage(), throwable);
        };
    }
}
