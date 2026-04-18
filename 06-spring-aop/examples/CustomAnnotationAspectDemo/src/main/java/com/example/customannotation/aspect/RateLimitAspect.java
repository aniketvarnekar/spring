/*
 * Enforces per-method rate limits for methods annotated with @RateLimit.
 *
 * Uses a ConcurrentHashMap to maintain a per-method token counter and the
 * timestamp of the last window reset. This is a simplified demonstration —
 * not suitable for production use where a proper rate-limiting library
 * (Resilience4j, Bucket4j) should be used.
 *
 * If the call limit for the current one-second window is exceeded,
 * throws RateLimitExceededException (a runtime exception).
 */
package com.example.customannotation.aspect;

import com.example.customannotation.annotation.RateLimit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private record Window(AtomicInteger count, long windowStartMs) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = pjp.getSignature().toLongString();
        long now = System.currentTimeMillis();

        windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMs() >= 1000) {
                return new Window(new AtomicInteger(0), now);
            }
            return existing;
        });

        Window window = windows.get(key);
        int calls = window.count().incrementAndGet();

        if (calls > rateLimit.callsPerSecond()) {
            log.warn("[RATE LIMIT] {} exceeded {} calls/s (calls={})",
                     pjp.getSignature().toShortString(), rateLimit.callsPerSecond(), calls);
            throw new RateLimitExceededException(
                "Rate limit exceeded for " + pjp.getSignature().toShortString()
                + " (" + calls + " > " + rateLimit.callsPerSecond() + " calls/s)");
        }

        return pjp.proceed();
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
