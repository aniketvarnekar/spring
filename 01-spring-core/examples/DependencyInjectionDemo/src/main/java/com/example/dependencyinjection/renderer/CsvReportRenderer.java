/*
 * Optional CSV implementation — registered conditionally to demonstrate ObjectProvider.
 *
 * This bean is registered unconditionally here, but in a real scenario it might be
 * guarded by @ConditionalOnProperty. ReportService uses ObjectProvider<CsvReportRenderer>
 * to tolerate its absence without a startup failure.
 */
package com.example.dependencyinjection.renderer;

import org.springframework.stereotype.Component;

@Component
public class CsvReportRenderer implements ReportRenderer {

    @Override
    public String render(String content) {
        return "\"content\"\n\"" + content + "\"";
    }

    @Override
    public String name() {
        return "CSV";
    }
}
