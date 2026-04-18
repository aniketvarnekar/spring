/*
 * HandlerInterceptor that measures and logs handler method execution time.
 *
 * Runs inside the DispatcherServlet, after the filter chain has passed control to it.
 * Unlike a filter, this interceptor has access to the resolved HandlerMethod,
 * enabling method-level context (controller name, method name, annotations).
 *
 * Key behavioral difference from a filter:
 *   postHandle is NOT called when the handler throws an exception.
 *   afterCompletion IS always called, even if an exception was thrown.
 */
package com.example.filterinterceptor.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class ExecutionTimeInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimeInterceptor.class);
    private static final String ATTR_START = "interceptor.startNanos";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) {
        request.setAttribute(ATTR_START, System.nanoTime());

        if (handler instanceof HandlerMethod hm) {
            // The interceptor can inspect the resolved handler — a filter cannot.
            log.info("[Interceptor] preHandle — {}.{}",
                    hm.getBeanType().getSimpleName(),
                    hm.getMethod().getName());
        }
        // Returning true continues the chain; false would abort processing.
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        // Not called if the handler threw an exception — use afterCompletion for cleanup.
        log.info("[Interceptor] postHandle — status={}", response.getStatus());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        long startNanos = (Long) request.getAttribute(ATTR_START);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        if (ex != null) {
            log.info("[Interceptor] afterCompletion — {}ms (exception: {})",
                    elapsedMs, ex.getClass().getSimpleName());
        } else {
            log.info("[Interceptor] afterCompletion — {}ms", elapsedMs);
        }
    }
}
