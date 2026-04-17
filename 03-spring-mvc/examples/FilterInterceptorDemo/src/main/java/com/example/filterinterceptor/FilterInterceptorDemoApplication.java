/*
 * Entry point for FilterInterceptorDemo.
 *
 * Shows the execution order of a Filter and a HandlerInterceptor for the same request.
 * Make a request to http://localhost:8080/api/hello and observe the console output:
 *
 *   [Filter]       doFilterInternal BEFORE chain
 *   [Interceptor]  preHandle
 *   [Controller]   handling request
 *   [Interceptor]  postHandle
 *   [Interceptor]  afterCompletion
 *   [Filter]       doFilterInternal AFTER chain
 *
 * The filter wraps the entire dispatch; the interceptor runs inside the dispatcher.
 */
package com.example.filterinterceptor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FilterInterceptorDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FilterInterceptorDemoApplication.class, args);
    }
}
