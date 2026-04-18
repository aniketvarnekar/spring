/*
 * Measures and logs execution time for methods annotated with @Timed.
 *
 * The @annotation(timed) pointcut pattern binds the @Timed annotation instance
 * directly into the advice method parameter. This allows reading annotation
 * attributes (timed.value()) inside the advice without reflection.
 *
 * If the annotation's value is empty, the method's short signature is used as
 * the operation name in the log output.
 */
package com.example.customannotation.aspect;

import com.example.customannotation.annotation.Timed;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TimedAspect {

    private static final Logger log = LoggerFactory.getLogger(TimedAspect.class);

    @Around("@annotation(timed)")
    public Object recordTime(ProceedingJoinPoint pjp, Timed timed) throws Throwable {
        // Resolve operation name: annotation value takes precedence; fall back to method name.
        String operationName = timed.value().isBlank()
                ? pjp.getSignature().toShortString()
                : timed.value();

        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("[TIMED] {} completed in {} ms", operationName, elapsedMs);
            return result;
        } catch (Throwable ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("[TIMED] {} failed in {} ms: {}", operationName, elapsedMs, ex.getMessage());
            throw ex;
        }
    }
}
