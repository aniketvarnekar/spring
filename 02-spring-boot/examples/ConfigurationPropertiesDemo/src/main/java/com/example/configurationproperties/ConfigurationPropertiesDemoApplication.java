/*
 * Entry point for ConfigurationPropertiesDemo.
 *
 * Demonstrates:
 *   1. @ConfigurationProperties binding a nested YAML structure to a typed object.
 *   2. JSR-380 validation (@NotBlank, @Min, @Valid) at startup.
 *   3. Relaxed binding — all of kebab-case, camelCase, and UPPER_SNAKE_CASE bind
 *      to the same Java field.
 *   4. Duration and List field binding.
 *
 * Try changing application.yaml to introduce a validation error (e.g., set
 * mail.port to -1) and observe that the application refuses to start.
 */
package com.example.configurationproperties;

import com.example.configurationproperties.properties.MailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ConfigurationPropertiesDemoApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
                SpringApplication.run(ConfigurationPropertiesDemoApplication.class, args);

        MailProperties mail = context.getBean(MailProperties.class);
        System.out.println("Mail host       : " + mail.getHost());
        System.out.println("Mail port       : " + mail.getPort());
        System.out.println("SMTP auth       : " + mail.getSmtp().isAuth());
        System.out.println("SMTP STARTTLS   : " + mail.getSmtp().isStarttlsEnable());
        System.out.println("Recipients      : " + mail.getDefaultRecipients());
        System.out.println("Connect timeout : " + mail.getConnectionTimeout());

        context.close();
    }
}
