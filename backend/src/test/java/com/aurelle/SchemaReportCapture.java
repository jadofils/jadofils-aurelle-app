package com.aurelle;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.execution.SchemaReport;

/** Captures Spring GraphQL's schema inspection report when the shared test context starts. */
@TestConfiguration(proxyBeanMethods = false)
public class SchemaReportCapture {

    private final AtomicReference<SchemaReport> report = new AtomicReference<>();

    @Bean
    GraphQlSourceBuilderCustomizer captureSchemaReport() {
        return builder -> builder.inspectSchemaMappings(report::set);
    }

    public Optional<SchemaReport> report() {
        return Optional.ofNullable(report.get());
    }
}
