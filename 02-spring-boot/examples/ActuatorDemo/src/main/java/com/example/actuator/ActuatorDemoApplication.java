/*
 * Entry point for ActuatorDemo.
 *
 * Demonstrates:
 *   1. Built-in Actuator endpoints exposed over HTTP (/actuator/health, /actuator/info, etc.)
 *   2. Custom HealthIndicator (ExternalServiceHealthIndicator)
 *   3. Custom @Endpoint (FeatureFlagsEndpoint) with @ReadOperation and @WriteOperation
 *
 * After starting, navigate to:
 *   http://localhost:8080/actuator          — lists all exposed endpoints
 *   http://localhost:8080/actuator/health   — aggregated health including custom indicator
 *   http://localhost:8080/actuator/featureflags — custom endpoint
 *
 * See application.yaml for endpoint exposure configuration.
 */
package com.example.actuator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ActuatorDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActuatorDemoApplication.class, args);
    }
}
