/*
 * Retry aspect driven by the @Retryable annotation.
 *
 * Algorithm:
 *   1. Attempt pjp.proceed().
 *   2. If the attempt succeeds, return the result.
 *   3. If the thrown exception is in the "on" list, sleep backoffMs * attempt and retry.
 *   4. If the thrown exception is NOT in the "on" list, re-throw immediately.
 *   5. If all attempts are exhausted, re-throw the last exception.
 *
 * Attempt count is logged at WARN level so failures are visible without enabling DEBUG.
 */
package com.example.retryaspect.aspect;

import com.example.retryaspect.annotation.Retryable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
public class RetryAspect {

    private static final Logger log = LoggerFactory.getLogger(RetryAspect.class);

    // Annotation binding: the Retryable annotation instance is passed as a parameter.
    @Around("@annotation(retryable)")
    public Object retry(ProceedingJoinPoint pjp, Retryable retryable) throws Throwable {
        int maxAttempts = retryable.maxAttempts();
        long backoffMs  = retryable.backoffMs();
        Set<Class<? extends Exception>> retryOn = Arrays.stream(retryable.on())
                .collect(Collectors.toSet());

        String method = pjp.getSignature().toShortString();
        Throwable lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return pjp.proceed();
            } catch (Throwable ex) {
                if (!isRetryable(ex, retryOn)) {
                    log.debug("Non-retryable exception from {}: {}", method, ex.getClass().getSimpleName());
                    throw ex;
                }
                lastException = ex;
                log.warn("Attempt {}/{} failed for {}: {}",
                         attempt, maxAttempts, method, ex.getMessage());

                if (attempt < maxAttempts) {
                    sleep(backoffMs * attempt);
                }
            }
        }

        log.warn("All {} attempts exhausted for {}", maxAttempts, method);
        throw lastException;
    }

    private boolean isRetryable(Throwable ex, Set<Class<? extends Exception>> retryOn) {
        return retryOn.stream().anyMatch(type -> type.isInstance(ex));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
