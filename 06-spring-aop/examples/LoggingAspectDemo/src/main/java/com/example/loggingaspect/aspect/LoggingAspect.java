/*
 * Logging aspect that intercepts all service and controller methods.
 *
 * Uses @Around advice to:
 *   - Log method entry with class name, method name, and arguments
 *   - Log successful exit with return value and elapsed time
 *   - Log exceptions with exception type and elapsed time before re-throwing
 *
 * Pointcut covers @Service and @RestController beans.
 * Named pointcuts are defined as void methods annotated with @Pointcut and composed
 * with || into a single combined pointcut used by the @Around advice.
 *
 * The aspect order is set low (close to lowest precedence) so that it runs inside
 * any transaction or security advice, seeing the real exception rather than a
 * rollback-wrapper exception.
 */
package com.example.loggingaspect.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Order(100)
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void serviceLayer() {}

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    public void controllerLayer() {}

    @Around("serviceLayer() || controllerLayer()")
    public Object logInvocation(ProceedingJoinPoint pjp) throws Throwable {
        String className  = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        Object[] args     = pjp.getArgs();

        log.debug("→ {}.{}({})", className, methodName, formatArgs(args));
        long start = System.nanoTime();

        try {
            Object result = pjp.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("← {}.{} returned {} in {} ms",
                      className, methodName, formatResult(result), elapsedMs);
            return result;
        } catch (Throwable ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("✗ {}.{} threw {} in {} ms: {}",
                     className, methodName,
                     ex.getClass().getSimpleName(), elapsedMs, ex.getMessage());
            throw ex;
        }
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        return Arrays.stream(args)
                .map(a -> a == null ? "null" : a.toString())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String formatResult(Object result) {
        if (result == null) {
            return "null";
        }
        String str = result.toString();
        // Truncate long results to avoid flooding the log.
        return str.length() > 200 ? str.substring(0, 200) + "…" : str;
    }
}
