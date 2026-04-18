/*
 * Report generation service demonstrating both custom annotations on the same method.
 *
 * generateReport: intercepted by @Timed (records duration) and @RateLimit (max 5/s).
 * computeTotal:   intercepted by @Timed only, using a custom operation name.
 *
 * Both @Timed and @RateLimit advice run for generateReport because the method
 * carries both annotations and both aspects use @annotation pointcuts.
 * The order between TimedAspect and RateLimitAspect is undefined (no @Order set),
 * but both are guaranteed to apply.
 */
package com.example.customannotation.service;

import com.example.customannotation.annotation.RateLimit;
import com.example.customannotation.annotation.Timed;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportService {

    @Timed
    @RateLimit(callsPerSecond = 5)
    public String generateReport(String type) {
        // Simulate work
        return "Report[" + type + "]";
    }

    @Timed("total-computation")
    public int computeTotal(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).sum();
    }
}
