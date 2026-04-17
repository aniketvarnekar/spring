/*
 * Entry point for BeanScopesDemo.
 *
 * Demonstrates three key scoping behaviors:
 *
 *   1. Singleton — the same instance is returned on every getBean() call.
 *   2. Prototype — a new instance is returned on every getBean() call.
 *   3. Prototype-into-singleton problem and the @Lookup solution:
 *      a singleton that needs a fresh prototype on every method call
 *      uses @Lookup method injection rather than @Autowired (which would
 *      capture a single prototype instance at startup).
 */
package com.example.beanscopes;

import com.example.beanscopes.service.SingletonService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class BeanScopesDemoApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
                SpringApplication.run(BeanScopesDemoApplication.class, args);

        demonstrateSingleton(context);
        demonstratePrototype(context);
        demonstrateLookup(context);

        context.close();
    }

    private static void demonstrateSingleton(ConfigurableApplicationContext ctx) {
        System.out.println("=== Singleton scope ===");
        // Both references point to the identical object — same identity hash code.
        SingletonService s1 = ctx.getBean(SingletonService.class);
        SingletonService s2 = ctx.getBean(SingletonService.class);
        System.out.println("Same instance? " + (s1 == s2));
        System.out.println();
    }

    private static void demonstratePrototype(ConfigurableApplicationContext ctx) {
        System.out.println("=== Prototype scope ===");
        // Each getBean() call produces a fresh instance.
        var p1 = ctx.getBean(com.example.beanscopes.model.PrototypeTask.class);
        var p2 = ctx.getBean(com.example.beanscopes.model.PrototypeTask.class);
        System.out.println("Same instance? " + (p1 == p2));
        System.out.println("IDs: " + p1.getId() + " vs " + p2.getId());
        System.out.println();
    }

    private static void demonstrateLookup(ConfigurableApplicationContext ctx) {
        System.out.println("=== @Lookup method injection ===");
        // SingletonService is a singleton, but createTask() returns a fresh
        // prototype on every call because Spring overrides the @Lookup method.
        SingletonService service = ctx.getBean(SingletonService.class);
        var t1 = service.createTask();
        var t2 = service.createTask();
        System.out.println("Same task instance from singleton? " + (t1 == t2));
        System.out.println("IDs: " + t1.getId() + " vs " + t2.getId());
    }
}
