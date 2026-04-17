/*
 * A prototype-scoped bean representing a stateful, per-use work unit.
 *
 * Prototype scope is appropriate when a bean accumulates per-call state
 * (like a builder or a command object) that must not be shared across callers.
 * The container creates a new instance on every getBean() call or @Lookup resolution.
 */
package com.example.beanscopes.model;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PrototypeTask {

    // Each instance carries a unique ID to make the "new instance per call" behavior
    // observable in the output without using identity hash codes.
    private final String id = UUID.randomUUID().toString().substring(0, 8);

    public String getId() {
        return id;
    }

    public void execute() {
        System.out.println("Executing task " + id);
    }
}
