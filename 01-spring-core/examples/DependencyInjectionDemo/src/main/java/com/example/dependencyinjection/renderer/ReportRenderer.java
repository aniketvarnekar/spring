/*
 * Marker interface for report rendering strategies.
 *
 * Multiple implementations of this interface are registered as beans,
 * which creates the ambiguity that @Primary and @Qualifier resolve.
 */
package com.example.dependencyinjection.renderer;

public interface ReportRenderer {
    String render(String content);
    String name();
}
