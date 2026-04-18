/*
 * Tests for the @Timed and @RateLimit annotation-driven aspects.
 *
 * TimedAspect: confirms that methods execute normally (timing is a side effect).
 * RateLimitAspect: confirms that calls within the limit succeed and that calls
 *   beyond the limit throw RateLimitExceededException.
 */
package com.example.customannotation;

import com.example.customannotation.aspect.RateLimitAspect.RateLimitExceededException;
import com.example.customannotation.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CustomAnnotationAspectTest {

    @Autowired
    private ReportService reportService;

    @Test
    void generateReport_withinRateLimit_returnsResult() {
        // 5 calls — at the limit (callsPerSecond = 5)
        for (int i = 0; i < 5; i++) {
            String result = reportService.generateReport("summary");
            assertThat(result).contains("summary");
        }
    }

    @Test
    void generateReport_exceedsRateLimit_throwsRateLimitExceededException() throws InterruptedException {
        // Wait to ensure we start a fresh window.
        Thread.sleep(1100);

        // First 5 calls should succeed.
        for (int i = 0; i < 5; i++) {
            reportService.generateReport("detail");
        }

        // The 6th call in the same second should be rejected.
        assertThatThrownBy(() -> reportService.generateReport("detail"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    void computeTotal_timedAnnotationWithCustomName_returnsCorrectSum() {
        int result = reportService.computeTotal(List.of(10, 20, 30));
        assertThat(result).isEqualTo(60);
    }
}
