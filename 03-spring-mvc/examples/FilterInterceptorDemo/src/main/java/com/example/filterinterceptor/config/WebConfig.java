/*
 * Registers the ExecutionTimeInterceptor with the Spring MVC interceptor registry.
 *
 * Interceptors are not auto-discovered by component scanning — they must be registered
 * explicitly via WebMvcConfigurer.addInterceptors(). Filters annotated with @Component
 * are auto-registered; interceptors are not.
 */
package com.example.filterinterceptor.config;

import com.example.filterinterceptor.interceptor.ExecutionTimeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ExecutionTimeInterceptor executionTimeInterceptor;

    public WebConfig(ExecutionTimeInterceptor executionTimeInterceptor) {
        this.executionTimeInterceptor = executionTimeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(executionTimeInterceptor)
                // Apply to all API paths; could exclude /actuator, /static, etc.
                .addPathPatterns("/**");
    }
}
