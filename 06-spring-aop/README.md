# 06 — Spring AOP

Aspect-Oriented Programming in the Spring context: proxy-based AOP, AspectJ pointcut expressions, advice types, aspect ordering, and practical use cases.

## Notes

| File | Topic |
|---|---|
| [01-aop-concepts.md](01-aop-concepts.md) | Core vocabulary: aspect, join point, pointcut, advice, weaving; Spring AOP vs AspectJ |
| [02-pointcuts.md](02-pointcuts.md) | Pointcut designators; `execution`, `within`, `@annotation`, `@within`, `bean`; combining expressions |
| [03-advice-types.md](03-advice-types.md) | `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`, `@Around`; `ProceedingJoinPoint` |
| [04-aspect-ordering.md](04-aspect-ordering.md) | `@Order`, `Ordered`, advice ordering within a single aspect, nested aspect call chain |
| [05-proxy-internals.md](05-proxy-internals.md) | JDK dynamic proxy vs CGLIB; when each is used; self-invocation problem; `proxyTargetClass` |
| [06-practical-use-cases.md](06-practical-use-cases.md) | Logging, metrics, retry, transaction demarcation, custom annotations as pointcut targets |

## Examples

| Project | Demonstrates |
|---|---|
| [LoggingAspectDemo](examples/LoggingAspectDemo/) | `@Around` advice logging method entry, exit, duration, and exceptions |
| [RetryAspectDemo](examples/RetryAspectDemo/) | `@Around` advice implementing a simple retry policy on `@Retryable`-annotated methods |
| [CustomAnnotationAspectDemo](examples/CustomAnnotationAspectDemo/) | Custom annotation as a pointcut target; reading annotation attributes inside advice |
