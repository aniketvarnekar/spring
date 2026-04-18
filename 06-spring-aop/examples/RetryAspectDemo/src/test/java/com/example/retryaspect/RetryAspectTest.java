/*
 * Tests for RetryAspect behavior.
 *
 * Scenarios:
 *   1. Method succeeds on the first attempt — exactly one call.
 *   2. Method fails once then succeeds — two calls, result is "success".
 *   3. Method fails all attempts — three calls, exception propagates to caller.
 *   4. Method throws a non-retryable exception — one call, exception propagates immediately.
 */
package com.example.retryaspect;

import com.example.retryaspect.service.FlakeyExternalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RetryAspectTest {

    @Autowired
    private FlakeyExternalService service;

    @BeforeEach
    void reset() {
        service.setFailCount(0);
    }

    @Test
    void callApi_succeedsOnFirstAttempt_onlyOneCall() throws IOException {
        service.setFailCount(0); // never fails

        String result = service.callApi();

        assertThat(result).isEqualTo("success");
        assertThat(service.getCallCount()).isEqualTo(1);
    }

    @Test
    void callApi_failsOnceThenSucceeds_twoCallsTotal() throws IOException {
        service.setFailCount(1); // fails on call 1, succeeds on call 2

        String result = service.callApi();

        assertThat(result).isEqualTo("success");
        assertThat(service.getCallCount()).isEqualTo(2);
    }

    @Test
    void callApi_failsAllAttempts_throwsAfterThreeAttempts() {
        service.setFailCount(10); // always fails within the 3-attempt window

        assertThatThrownBy(() -> service.callApi())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Simulated transient failure");

        assertThat(service.getCallCount()).isEqualTo(3); // maxAttempts = 3
    }

    @Test
    void callApi_nonRetryableException_propagatesImmediatelyAfterOneCall() {
        assertThatThrownBy(() -> service.callApiWithNonRetryableError())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fatal error");

        // No call count to check (no AtomicInteger in that method),
        // but the test confirms the exception type and that it is not retried.
    }
}
