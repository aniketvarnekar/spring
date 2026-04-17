/*
 * Custom Actuator endpoint for reading and toggling feature flags at runtime.
 *
 * @Endpoint registers the class as an Actuator endpoint accessible over both
 * HTTP and JMX. The "id" attribute sets the URL path segment:
 *   GET  /actuator/featureflags           — lists all flags (@ReadOperation)
 *   GET  /actuator/featureflags/{name}    — reads one flag (@ReadOperation + @Selector)
 *   POST /actuator/featureflags/{name}    — toggles a flag (@WriteOperation + @Selector)
 *
 * The endpoint must be exposed in application.yaml under
 * management.endpoints.web.exposure.include.
 */
package com.example.actuator.endpoint;

import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Endpoint(id = "featureflags")
public class FeatureFlagsEndpoint {

    // In-memory flag store for the demo — replace with a real feature flag service.
    private final Map<String, Boolean> flags = new ConcurrentHashMap<>(Map.of(
            "new-checkout-flow", false,
            "recommendation-engine", true,
            "dark-mode", true
    ));

    @ReadOperation
    public Map<String, Boolean> allFlags() {
        return Map.copyOf(flags);
    }

    @ReadOperation
    public Boolean flagByName(@Selector String name) {
        // Returns null if the flag does not exist — Actuator serializes null as HTTP 404.
        return flags.get(name);
    }

    @WriteOperation
    public void setFlag(@Selector String name, boolean enabled) {
        // WriteOperation maps to HTTP POST with a JSON body: {"enabled": true}
        flags.put(name, enabled);
    }

    @DeleteOperation
    public void removeFlag(@Selector String name) {
        flags.remove(name);
    }
}
