/*
 * Service interface representing a greeting capability.
 *
 * The auto-configuration provides a default implementation (DefaultGreetingService).
 * If the consuming application declares any bean of this type, the auto-configuration
 * backs off and yields to the user-provided implementation.
 */
package com.example.autoconfiguration.greeting;

public interface GreetingService {
    String greet(String name);
}
