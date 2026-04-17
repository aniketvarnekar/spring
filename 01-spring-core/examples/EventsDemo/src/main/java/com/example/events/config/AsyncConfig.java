/*
 * Enables Spring's asynchronous method execution capability.
 *
 * @EnableAsync activates the AOP infrastructure that intercepts @Async-annotated
 * methods and dispatches them to a TaskExecutor. Without this annotation, @Async
 * annotations on event listeners and service methods are silently ignored.
 *
 * The custom executor defined here uses named threads so the demo output shows
 * "async-" thread names, confirming that async dispatch actually occurred.
 */
package com.example.events.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        // Thread names prefixed with "async-" so they are visible in the demo output.
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
