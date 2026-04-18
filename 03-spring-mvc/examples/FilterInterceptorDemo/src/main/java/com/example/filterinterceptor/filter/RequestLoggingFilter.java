/*
 * Servlet filter that logs each request with timing.
 *
 * Extends OncePerRequestFilter so it runs exactly once per request regardless of
 * internal forwards (e.g., error dispatching). The filter wraps the entire dispatch
 * cycle — it executes before and after the DispatcherServlet and all interceptors.
 */
package com.example.filterinterceptor.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long start = System.currentTimeMillis();

        // Attach a correlation ID to the response so clients can reference it in support.
        response.setHeader("X-Request-Id", requestId);

        log.info("[Filter] [{}}] BEFORE dispatch — {} {}",
                requestId, request.getMethod(), request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } finally {
            // finally ensures this runs even if the chain throws an exception.
            long elapsed = System.currentTimeMillis() - start;
            log.info("[Filter] [{}] AFTER dispatch — status={} elapsed={}ms",
                    requestId, response.getStatus(), elapsed);
        }
    }
}
