/*
 * Tests for GreetingAutoConfiguration using ApplicationContextRunner.
 *
 * ApplicationContextRunner creates and runs a minimal ApplicationContext in-process,
 * without starting a Spring Boot application. It is the correct tool for testing
 * auto-configuration conditions: fast, isolated, and requiring no server or database.
 *
 * The tests verify:
 *   1. The auto-configuration registers a GreetingService when none is user-provided.
 *   2. The auto-configuration backs off when the user provides a GreetingService bean.
 *   3. The greeting.message property is applied correctly.
 */
package com.example.autoconfiguration;

import com.example.autoconfiguration.autoconfigure.GreetingAutoConfiguration;
import com.example.autoconfiguration.greeting.GreetingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GreetingAutoConfigurationTest {

    // The runner is configured once and shared across all test methods.
    // Each run() call produces a fresh context, so tests are independent.
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(GreetingAutoConfiguration.class));

    @Test
    void autoConfigures_greetingService_whenNoBeanPresent() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(GreetingService.class);
            assertThat(ctx.getBean(GreetingService.class).greet("Test"))
                    .isEqualTo("Hello, Test!");
        });
    }

    @Test
    void appliesCustomMessage_fromProperty() {
        contextRunner
            .withPropertyValues("greeting.message=Howdy")
            .run(ctx -> {
                assertThat(ctx.getBean(GreetingService.class).greet("Partner"))
                        .isEqualTo("Howdy, Partner!");
            });
    }

    @Test
    void backsOff_whenUserProvidesGreetingService() {
        // The user's configuration overrides the auto-configured bean.
        contextRunner
            .withUserConfiguration(UserGreetingConfig.class)
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(GreetingService.class);
                // The user's bean returns "Custom: ..." — not the auto-configured default.
                assertThat(ctx.getBean(GreetingService.class).greet("World"))
                        .startsWith("Custom: ");
            });
    }

    // A simulated user-provided configuration — replaces the auto-configured bean.
    @Configuration
    static class UserGreetingConfig {
        @Bean
        GreetingService customGreetingService() {
            return name -> "Custom: Hello, " + name;
        }
    }
}
