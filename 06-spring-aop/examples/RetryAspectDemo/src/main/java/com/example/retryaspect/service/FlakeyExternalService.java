/*
 * Simulates an unreliable external service that fails a configurable number of times
 * before succeeding. Used to demonstrate and test the RetryAspect.
 *
 * The failCount field controls how many times the method throws before returning.
 * Tests reset it via setFailCount() before each scenario.
 */
package com.example.retryaspect.service;

import com.example.retryaspect.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FlakeyExternalService {

    private final AtomicInteger callCount     = new AtomicInteger(0);
    private volatile int         failCount    = 0;

    /**
     * Fails the first {@code failCount} times, then returns "success".
     * Annotated with @Retryable to trigger RetryAspect.
     */
    @Retryable(maxAttempts = 3, backoffMs = 10, on = {IOException.class})
    public String callApi() throws IOException {
        int call = callCount.incrementAndGet();
        if (call <= failCount) {
            throw new IOException("Simulated transient failure on call #" + call);
        }
        return "success";
    }

    /**
     * Always throws an IllegalStateException — not in the retryable exception list.
     * RetryAspect should propagate this immediately without retrying.
     */
    @Retryable(maxAttempts = 3, backoffMs = 10, on = {IOException.class})
    public String callApiWithNonRetryableError() {
        throw new IllegalStateException("Fatal error — do not retry");
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
        this.callCount.set(0);
    }

    public int getCallCount() {
        return callCount.get();
    }
}
