# Spring Core

This section covers the foundational layer of the Spring Framework: how the IoC container manages bean definitions and their lifecycles, the mechanics of dependency injection, the full range of bean scopes, configuration styles, the environment abstraction, and the event system. Understanding these internals is a prerequisite for reasoning clearly about everything else Spring does.

## Notes

| File | Description |
|------|-------------|
| [01-ioc-container.md](./01-ioc-container.md) | ApplicationContext vs BeanFactory, BeanDefinition internals, container bootstrap |
| [02-dependency-injection.md](./02-dependency-injection.md) | Constructor, setter, and field injection; @Autowired resolution; circular dependencies |
| [03-bean-lifecycle.md](./03-bean-lifecycle.md) | Full instantiation-to-destruction sequence; BeanPostProcessor vs BeanFactoryPostProcessor |
| [04-bean-scopes.md](./04-bean-scopes.md) | Singleton, prototype, web scopes, scoped proxies, custom scopes |
| [05-configuration-styles.md](./05-configuration-styles.md) | @Configuration full/lite mode, component scanning, @Import, @ImportResource |
| [06-environment-and-profiles.md](./06-environment-and-profiles.md) | PropertySource hierarchy, @Value, @ConfigurationProperties, @Profile, @Conditional |
| [07-events.md](./07-events.md) | ApplicationEvent, @EventListener, async and ordered listeners, generic events |

## Examples

| Project | Description |
|---------|-------------|
| [BeanLifecycleDemo](./examples/BeanLifecycleDemo/) | Demonstrates the full bean lifecycle sequence with console output at each phase |
| [DependencyInjectionDemo](./examples/DependencyInjectionDemo/) | Compares constructor, setter, and field injection; shows @Qualifier and @Primary resolution |
| [BeanScopesDemo](./examples/BeanScopesDemo/) | Illustrates singleton vs prototype behavior and the scoped-proxy solution |
| [ProfilesDemo](./examples/ProfilesDemo/) | Shows profile-specific beans and PropertySource overriding |
| [EventsDemo](./examples/EventsDemo/) | Publishes and receives custom events synchronously and asynchronously |
