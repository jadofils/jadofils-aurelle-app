package com.aurelle.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelle.IntegrationTest;
import com.aurelle.SchemaReportCapture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.SchemaReport;

/**
 * The served schema is the packaged contract files, and every field in it has a resolver.
 * Fails when a contract file is packaged (backend/pom.xml) before its resolvers exist.
 */
class SchemaMappingIT extends IntegrationTest {

    @Autowired
    SchemaReportCapture capture;

    @Autowired
    GraphQlSource graphQlSource;

    @Test
    void everySchemaFieldHasAResolver() {
        SchemaReport report = capture.report().orElseThrow();
        assertThat(report.unmappedFields()).as(report.toString()).isEmpty();
        assertThat(report.unmappedRegistrations()).as(report.toString()).isEmpty();
        assertThat(report.unmappedArguments()).as(report.toString()).isEmpty();
    }

    @Test
    void servesTheContractQueries() {
        var queryType = graphQlSource.schema().getQueryType();
        assertThat(queryType.getFieldDefinition("serverInfo")).isNotNull();
        assertThat(queryType.getFieldDefinition("shopSettings")).isNotNull();
        assertThat(graphQlSource.schema().getType("Money")).isNotNull();
    }
}
