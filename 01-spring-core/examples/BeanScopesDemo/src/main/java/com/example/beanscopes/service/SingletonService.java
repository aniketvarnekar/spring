/*
 * A singleton service that produces fresh PrototypeTask instances on demand.
 *
 * The naive approach — @Autowired PrototypeTask task — would inject one prototype
 * at startup and reuse it forever, defeating the prototype scope. @Lookup solves
 * this: Spring subclasses SingletonService at runtime and overrides createTask()
 * to return ctx.getBean(PrototypeTask.class) on every invocation.
 *
 * The method is abstract to make it impossible to call super() accidentally and
 * to make the subclassing intent explicit. It can also be concrete (returning null
 * or any value), since Spring's override replaces the body entirely.
 */
package com.example.beanscopes.service;

import com.example.beanscopes.model.PrototypeTask;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Service;

@Service
public abstract class SingletonService {

    // Spring overrides this method at runtime to return ctx.getBean(PrototypeTask.class).
    // The declaring class must not be final, and the method must not be static, private,
    // or final — all of which would prevent CGLIB from overriding it.
    @Lookup
    public abstract PrototypeTask createTask();

    public void processTasks(int count) {
        for (int i = 0; i < count; i++) {
            // Each call to createTask() goes through the CGLIB override —
            // a fresh PrototypeTask arrives each time.
            PrototypeTask task = createTask();
            task.execute();
        }
    }
}
