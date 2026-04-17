/*
 * Typed configuration properties for the "mail" prefix.
 *
 * Demonstrates several binding features:
 *
 *   - Nested static inner class (Smtp) bound from mail.smtp.*
 *   - List<String> bound from a YAML sequence
 *   - Duration bound from a string like "5s" or "PT5S"
 *   - JSR-380 constraints validated at startup via @Validated
 *   - @Valid on the nested Smtp field propagates validation into it
 *
 * The @ConfigurationPropertiesScan on the application class discovers this class
 * automatically without requiring @EnableConfigurationProperties in a @Configuration.
 */
package com.example.configurationproperties.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "mail")
@Validated
public class MailProperties {

    @NotBlank
    private String host;

    @Min(1) @Max(65535)
    private int port = 25;

    // @Valid propagates JSR-380 validation into the nested Smtp object's fields.
    @Valid
    private Smtp smtp = new Smtp();

    private List<String> defaultRecipients = List.of();

    // Duration binds from "5s", "2m", "PT5S" etc. — see Spring's DurationStyle.
    private Duration connectionTimeout = Duration.ofSeconds(10);

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public Smtp getSmtp() { return smtp; }
    public void setSmtp(Smtp smtp) { this.smtp = smtp; }

    public List<String> getDefaultRecipients() { return defaultRecipients; }
    public void setDefaultRecipients(List<String> defaultRecipients) {
        this.defaultRecipients = defaultRecipients;
    }

    public Duration getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public static class Smtp {

        private boolean auth;

        // "starttls-enable" in YAML binds here via relaxed binding (camelCase normalization).
        private boolean starttlsEnable;

        public boolean isAuth() { return auth; }
        public void setAuth(boolean auth) { this.auth = auth; }

        public boolean isStarttlsEnable() { return starttlsEnable; }
        public void setStarttlsEnable(boolean starttlsEnable) {
            this.starttlsEnable = starttlsEnable;
        }
    }
}
