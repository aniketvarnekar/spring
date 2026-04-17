/*
 * Entry point for the DependencyInjectionDemo application.
 *
 * This demo shows:
 *   1. Constructor injection (preferred) — ReportService
 *   2. @Qualifier to disambiguate multiple beans of the same type
 *   3. @Primary as a default-selection mechanism
 *   4. ObjectProvider for optional/lazy dependency resolution
 *
 * Run the application and check the console to see which implementations
 * were injected and how Spring resolved the ambiguities.
 */
package com.example.dependencyinjection;

import com.example.dependencyinjection.service.ReportService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DependencyInjectionDemoApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
                SpringApplication.run(DependencyInjectionDemoApplication.class, args);

        ReportService reportService = context.getBean(ReportService.class);
        reportService.generateAll();

        context.close();
    }
}
