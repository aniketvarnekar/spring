/*
 * Service demonstrating three injection scenarios:
 *
 *   1. Constructor injection of the @Primary implementation (no qualifier needed).
 *   2. Constructor injection of a specific implementation via @Qualifier.
 *   3. ObjectProvider for an optional dependency — resolves to null gracefully
 *      if no CsvReportRenderer bean is registered.
 *
 * All three fields are final, which is only possible with constructor injection.
 * This is a key advantage over setter and field injection styles.
 */
package com.example.dependencyinjection.service;

import com.example.dependencyinjection.renderer.CsvReportRenderer;
import com.example.dependencyinjection.renderer.ReportRenderer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    // Receives HtmlReportRenderer because it is @Primary among all ReportRenderer beans.
    private final ReportRenderer defaultRenderer;

    // Receives PdfReportRenderer regardless of @Primary — @Qualifier takes precedence.
    private final ReportRenderer pdfRenderer;

    // ObjectProvider resolves lazily and returns null via getIfAvailable()
    // if no CsvReportRenderer bean exists — no startup failure.
    private final ObjectProvider<CsvReportRenderer> csvRendererProvider;

    public ReportService(
            ReportRenderer defaultRenderer,
            @Qualifier("pdfReportRenderer") ReportRenderer pdfRenderer,
            ObjectProvider<CsvReportRenderer> csvRendererProvider) {

        this.defaultRenderer = defaultRenderer;
        this.pdfRenderer = pdfRenderer;
        this.csvRendererProvider = csvRendererProvider;
    }

    public void generateAll() {
        String content = "Q4 Financial Summary";

        System.out.println("Default renderer  : " + defaultRenderer.name());
        System.out.println("Default output    : " + defaultRenderer.render(content));
        System.out.println();

        System.out.println("PDF renderer      : " + pdfRenderer.name());
        System.out.println("PDF output        : " + pdfRenderer.render(content));
        System.out.println();

        // ObjectProvider.getIfAvailable() returns null if the bean is absent
        // rather than throwing NoSuchBeanDefinitionException.
        CsvReportRenderer csv = csvRendererProvider.getIfAvailable();
        if (csv != null) {
            System.out.println("CSV renderer      : " + csv.name());
            System.out.println("CSV output        : " + csv.render(content));
        } else {
            System.out.println("CSV renderer      : not available");
        }
    }
}
