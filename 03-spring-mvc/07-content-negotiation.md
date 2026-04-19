# Content Negotiation

## Overview

Content negotiation is the mechanism by which the server and client agree on the format of the response. The client expresses its preferences via the `Accept` request header; the server selects the most appropriate format from those it can produce and sets the `Content-Type` response header accordingly. Spring MVC implements content negotiation in `ContentNegotiationManager`, which delegates to a chain of `ContentNegotiationStrategy` implementations.

By default, Spring MVC uses the `Accept` header as the sole negotiation signal. Spring Boot's auto-configuration enables the header-based strategy and disables the older URL extension strategy (`/resource.json`, `/resource.xml`) by default — URL extension strategies are deprecated and removed from the default configuration in Spring Framework 6.

`HttpMessageConverter` implementations are the bridge between Java types and serialized formats. When a handler method returns a value, the `RequestMappingHandlerAdapter` iterates through the registered converters looking for one that supports both the return type and a media type acceptable to the client. The converter writes the serialized form to the response output stream.

Adding support for new response formats — for example, CSV for tabular data or YAML for configuration endpoints — means registering a custom `HttpMessageConverter`. The converter declares which media types it supports, and the content negotiation system routes matching `Accept` headers to it automatically.

## Key Concepts

### Accept Header Strategy

```text
Client sends:  Accept: application/json, application/xml;q=0.9, */*;q=0.8

Spring evaluates converters in order:
  1. Find all converters that can write the handler's return type
  2. Find the intersection of those converters' media types with the client's accepted types
  3. Select the highest-priority (q-factor weighted) media type
  4. Use the corresponding converter to serialize the response
```

Quality factors (`q=0.9`) allow clients to express preferences. A missing `q` defaults to `1.0`. When two converters can serve the same media type, converter registration order determines priority.

### Configuring HttpMessageConverters

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // extendMessageConverters adds to the default list without replacing it.
        // configureMessageConverters REPLACES the entire default list — use carefully.

        // Move Jackson to position 0 to give it higher priority than StringHttpMessageConverter
        // for String return types (otherwise plain strings might be returned as text/plain).
        converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        converters.add(0, new MappingJackson2HttpMessageConverter(customObjectMapper()));
    }

    private ObjectMapper customObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

### Custom HttpMessageConverter

A converter for a hypothetical CSV media type:

```java
public class CsvHttpMessageConverter extends AbstractHttpMessageConverter<List<?>> {

    public CsvHttpMessageConverter() {
        // Register the media type this converter handles
        super(new MediaType("text", "csv"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        // This converter handles any List
        return List.class.isAssignableFrom(clazz);
    }

    @Override
    protected List<?> readInternal(Class<? extends List<?>> clazz,
                                    HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        // Reading CSV from request body (for @RequestBody)
        throw new UnsupportedOperationException("CSV reading not implemented");
    }

    @Override
    protected void writeInternal(List<?> list,
                                  HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        // Writing CSV to response body
        try (PrintWriter writer = new PrintWriter(outputMessage.getBody())) {
            for (Object item : list) {
                writer.println(toCsvLine(item));
            }
        }
    }

    private String toCsvLine(Object item) {
        // Simplified — use a real CSV library in production
        return item.toString();
    }
}

// Registration
@Override
public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    converters.add(new CsvHttpMessageConverter());
}
```

With this converter registered, a handler returning `List<ReportRow>` will produce CSV output when the client sends `Accept: text/csv`.

### Produces Constraint and Negotiation

```java
@GetMapping(value = "/report", produces = {
        MediaType.APPLICATION_JSON_VALUE,
        "text/csv"
})
public List<ReportRow> report() {
    return reportService.generate();
}
```

The `produces` attribute limits which media types this specific handler will serve. If the client sends `Accept: application/xml` and the handler only produces `application/json` and `text/csv`, the handler does not match — Spring returns `406 Not Acceptable`.

### Content Negotiation Configuration

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
            // Use only Accept header (default)
            .favorPathExtension(false)     // deprecated and off by default — keep off
            .favorParameter(false)          // ?format=json — off by default
            .ignoreAcceptHeader(false)      // never disable unless you have a reason
            // Default media type when Accept is *//* or absent
            .defaultContentType(MediaType.APPLICATION_JSON);
    }
}
```

## Gotchas

`MappingJackson2HttpMessageConverter` supports both `application/json` and any `application/*+json` media type (JSON-based structured syntaxes). When the client sends `Accept: application/problem+json`, Jackson will write the response — `ProblemDetail` is serialized as standard JSON with the `application/problem+json` content type set by Spring's `ProblemDetail` machinery.

A converter that supports `*/*` (wildcard) will match any `Accept` header and may produce an unexpected format. `StringHttpMessageConverter` supports `*/*` by default, which means a handler returning `String` might be serialized as `text/plain` even when the client requests `application/json`. Configuring `StringHttpMessageConverter` to support only `text/plain` and `text/html` prevents this ambiguity.

The order of `HttpMessageConverter` registrations matters for ambiguous cases. When two converters can both produce the accepted media type, the first one in the list wins. Default converter order places `ByteArrayHttpMessageConverter` and `StringHttpMessageConverter` before `MappingJackson2HttpMessageConverter`. For JSON-first APIs, move Jackson to position 0 to ensure it takes precedence.

When `configureMessageConverters()` is used instead of `extendMessageConverters()`, it clears the entire default list and the caller is responsible for registering all converters including Jackson. Forgetting to add `MappingJackson2HttpMessageConverter` causes all JSON serialization to fail with `406 Not Acceptable`.

Content negotiation for `@RequestBody` is driven by the request's `Content-Type` header, not `Accept`. If a client sends `Content-Type: text/xml` and no converter supports XML deserialization, Spring returns `415 Unsupported Media Type`. The `consumes` attribute on `@RequestMapping` narrows which content types a handler accepts and provides a cleaner error message than leaving it to converter matching.
