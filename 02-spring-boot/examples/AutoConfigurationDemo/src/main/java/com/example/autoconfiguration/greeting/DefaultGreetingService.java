/*
 * Default implementation of GreetingService, provided by the auto-configuration.
 *
 * This class is not a @Component — it is instantiated explicitly as a @Bean in
 * GreetingAutoConfiguration. This ensures it is only created when the auto-configuration
 * conditions are satisfied, not by component scanning, which would bypass the conditions.
 */
package com.example.autoconfiguration.greeting;

public class DefaultGreetingService implements GreetingService {

    private final String message;

    public DefaultGreetingService(String message) {
        this.message = message;
    }

    @Override
    public String greet(String name) {
        return message + ", " + name + "!";
    }
}
