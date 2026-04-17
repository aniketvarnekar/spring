/*
 * HTML implementation of ReportRenderer.
 *
 * Annotated with @Primary so that injection points of type ReportRenderer
 * without an explicit @Qualifier receive this implementation by default.
 * @Primary does not prevent @Qualifier from selecting a different implementation.
 */
package com.example.dependencyinjection.renderer;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class HtmlReportRenderer implements ReportRenderer {

    @Override
    public String render(String content) {
        return "<html><body>" + content + "</body></html>";
    }

    @Override
    public String name() {
        return "HTML";
    }
}
