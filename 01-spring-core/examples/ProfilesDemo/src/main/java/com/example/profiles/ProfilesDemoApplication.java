/*
 * Entry point for ProfilesDemo.
 *
 * Demonstrates:
 *   1. Profile-specific @Bean registration — different DataSource per environment.
 *   2. Profile-specific application-{profile}.yaml overrides.
 *   3. @Profile with profile expressions (NOT, AND).
 *
 * By default, the "dev" profile is active (set in application.yaml).
 * To activate the "prod" profile instead, run with:
 *
 *   --spring.profiles.active=prod
 *
 * or set the environment variable SPRING_PROFILES_ACTIVE=prod.
 */
package com.example.profiles;

import com.example.profiles.datasource.DataSourceInfo;
import com.example.profiles.service.NotificationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ProfilesDemoApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
                SpringApplication.run(ProfilesDemoApplication.class, args);

        DataSourceInfo dsInfo = context.getBean(DataSourceInfo.class);
        System.out.println("Active datasource: " + dsInfo.describe());

        NotificationService notifier = context.getBean(NotificationService.class);
        notifier.send("Startup complete");

        context.close();
    }
}
