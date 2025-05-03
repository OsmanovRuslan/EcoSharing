package ru.ecosharing.notification_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфигурация для асинхронных операций (@Async).
 * Позволяет настроить пул потоков и обработку ошибок.
 * Класс опционален, если устраивают дефолтные настройки Spring или настройки через properties.
 */
@Slf4j
@Configuration
@EnableAsync // Включает обработку аннотации @Async
public class AsyncConfig implements AsyncConfigurer {

    // Читаем настройки пула из application.yml (с дефолтными значениями)
    @Value("${spring.task.execution.pool.core-size:5}")
    private int corePoolSize;

    @Value("${spring.task.execution.pool.max-size:15}")
    private int maxPoolSize;

    @Value("${spring.task.execution.pool.queue-capacity:200}")
    private int queueCapacity;

    @Value("${spring.task.execution.thread-name-prefix:notif-async-}")
    private String threadNamePrefix;

    /**
     * Предоставляет кастомный Executor для @Async методов.
     * @return Настроенный ThreadPoolTaskExecutor.
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("Настроен кастомный ThreadPoolTaskExecutor для @Async: core={}, max={}, queue={}, prefix='{}'",
                corePoolSize, maxPoolSize, queueCapacity, threadNamePrefix);
        return executor;
    }

    /**
     * Предоставляет обработчик для неперехваченных исключений в @Async методах.
     * @return AsyncUncaughtExceptionHandler.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    /**
     * Кастомный обработчик ошибок для логирования исключений в асинхронных задачах.
     */
    public static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable throwable, Method method, Object... obj) {
            log.error("--- Async Exception ---");
            log.error("Исключение в методе '{}': {}", method.getName(), throwable.getMessage());
            log.error("Параметры вызова: {}", Arrays.toString(obj));
            log.error("Полный стектрейс:", throwable); // Логируем полный стектрейс
            log.error("-----------------------");
        }
    }
}