/*
 * Entry point for AutoConfigurationDemo.
 *
 * This demo defines a custom auto-configuration (GreetingAutoConfiguration) and
 * shows two behaviors:
 *
 *   1. The auto-configured GreetingService bean is active when no user-declared
 *      bean of the same type is present.
 *   2. The auto-configuration backs off when the user declares their own bean.
 *
 * The ApplicationContextRunner tests (GreetingAutoConfigurationTest) verify
 * this backoff behavior in isolation, without starting the full Spring Boot app.
 *
 * Running the main class shows the auto-configured bean in action.
 */
package com.example.autoconfiguration;

import com.example.autoconfiguration.greeting.GreetingService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class AutoConfigurationDemoApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
                SpringApplication.run(AutoConfigurationDemoApplication.class, args);

        GreetingService greetingService = context.getBean(GreetingService.class);
        System.out.println(greetingService.greet("World"));

        context.close();
    }
}
