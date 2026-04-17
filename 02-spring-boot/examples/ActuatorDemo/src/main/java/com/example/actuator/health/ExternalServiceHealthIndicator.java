/*
 * Custom HealthIndicator that simulates checking an external service.
 *
 * Spring Boot discovers all beans implementing HealthIndicator and includes their
 * results in the aggregated /actuator/health response. The bean name (without
 * the "HealthIndicator" suffix) becomes the component name in the response:
 * this bean appears as "externalService" in the health output.
 *
 * In this demo, the service is always reported as UP with simulated latency.
 * In a real implementation, this would call client.ping() or check a connection pool.
 */
package com.example.actuator.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ExternalServiceHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // Simulate a reachability check — in production this would be a real network call.
        boolean reachable = checkExternalService();

        if (reachable) {
            return Health.up()
                    .withDetail("service", "payment-gateway")
                    .withDetail("latency", "12ms")
                    .build();
        }

        return Health.down()
                .withDetail("service", "payment-gateway")
                .withDetail("reason", "connection refused")
                .build();
    }

    private boolean checkExternalService() {
        // Simulated: always reachable in the demo.
        return true;
    }
}
