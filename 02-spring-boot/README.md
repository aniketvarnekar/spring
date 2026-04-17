# Spring Boot

This section covers the mechanisms that make Spring Boot distinct from plain Spring: the auto-configuration system, the starter dependency model, type-safe configuration properties, the Actuator observability layer, the embedded server, and the full testing toolkit. The goal is to move past "it just works" and understand the conditions, ordering, and extension points that control what Boot configures and why.

## Notes

| File | Description |
|------|-------------|
| [01-auto-configuration.md](./01-auto-configuration.md) | How @EnableAutoConfiguration works, AutoConfiguration.imports, conditional hierarchy, debugging and writing custom auto-configs |
| [02-starters.md](./02-starters.md) | What starters are, transitive dependency management, creating custom starters and auto-configuration modules |
| [03-configuration-properties.md](./03-configuration-properties.md) | @ConfigurationProperties binding, nested types, validation, relaxed binding, IDE metadata |
| [04-actuator.md](./04-actuator.md) | Built-in endpoints, security, custom endpoints, Micrometer metrics, custom HealthIndicator |
| [05-embedded-server.md](./05-embedded-server.md) | Tomcat vs Jetty vs Undertow, customization via ServerProperties and WebServerFactoryCustomizer, TLS, HTTP/2 |
| [06-testing.md](./06-testing.md) | @SpringBootTest modes, slice tests, @MockBean, MockMvc, TestRestTemplate, ApplicationContextRunner |

## Examples

| Project | Description |
|---------|-------------|
| [AutoConfigurationDemo](./examples/AutoConfigurationDemo/) | Writes a custom auto-configuration with @ConditionalOnMissingBean and verifies it with ApplicationContextRunner |
| [ConfigurationPropertiesDemo](./examples/ConfigurationPropertiesDemo/) | Binds nested YAML to a @ConfigurationProperties class with JSR-380 validation |
| [ActuatorDemo](./examples/ActuatorDemo/) | Exposes built-in actuator endpoints and adds a custom health indicator and read/write endpoint |
| [SpringBootTestDemo](./examples/SpringBootTestDemo/) | Shows @SpringBootTest, @WebMvcTest, @MockBean, and ApplicationContextRunner in test code |
