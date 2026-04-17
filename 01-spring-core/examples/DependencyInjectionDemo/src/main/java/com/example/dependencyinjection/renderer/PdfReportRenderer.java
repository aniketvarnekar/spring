/*
 * PDF implementation of ReportRenderer.
 *
 * Because HtmlReportRenderer is @Primary, injection points that simply declare
 * ReportRenderer will receive HtmlReportRenderer. To obtain this bean, the
 * injection point must use @Qualifier("pdfReportRenderer") — the default bean
 * name derived from the class name by Spring's naming strategy.
 */
package com.example.dependencyinjection.renderer;

import org.springframework.stereotype.Component;

@Component
public class PdfReportRenderer implements ReportRenderer {

    @Override
    public String render(String content) {
        return "[PDF] " + content;
    }

    @Override
    public String name() {
        return "PDF";
    }
}
