/*
 * Configuration properties for the greeting auto-configuration.
 *
 * Bound from the "greeting" prefix in application.yaml/properties.
 * The auto-configuration registers this class via @EnableConfigurationProperties
 * so it does not need to be a @Component or @ConfigurationProperties-scanned.
 */
package com.example.autoconfiguration.greeting;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "greeting")
public class GreetingProperties {

    // Default message used if the user does not override greeting.message
    private String message = "Hello";

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
