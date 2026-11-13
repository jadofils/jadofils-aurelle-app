package com.aurelle.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelle.common.ErrorCode;
import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.language.SourceLocation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.graphql.execution.ErrorType;

class GraphQlErrorNormalizerTest {

    private final GraphQlErrorNormalizer normalizer = new GraphQlErrorNormalizer();

    @ParameterizedTest
    @CsvSource({
        "InvalidSyntax, BAD_REQUEST",
        "ValidationError, BAD_REQUEST",
        "OperationNotSupported, BAD_REQUEST",
        "ExecutionAborted, QUERY_TOO_COMPLEX",
        "DataFetchingException, INTERNAL",
        "NullValueInNonNullableField, INTERNAL"})
    void mapsGraphqlJavaClassifications(graphql.ErrorType type, ErrorCode expected) {
        assertThat(codeOf(normalizer.normalize(error(type, null)))).isEqualTo(expected.name());
    }

    @ParameterizedTest
    @CsvSource({
        "BAD_REQUEST, BAD_REQUEST",
        "UNAUTHORIZED, UNAUTHENTICATED",
        "FORBIDDEN, FORBIDDEN",
        "NOT_FOUND, NOT_FOUND",
        "INTERNAL_ERROR, INTERNAL"})
    void mapsSpringClassifications(ErrorType type, ErrorCode expected) {
        assertThat(codeOf(normalizer.normalize(error(type, null)))).isEqualTo(expected.name());
    }

    @Test
    void unknownClassificationsAreInternal() {
        ErrorClassification custom = new ErrorClassification() { };

        assertThat(codeOf(normalizer.normalize(error(custom, null)))).isEqualTo("INTERNAL");
    }

    @Test
    void missingClassificationIsInternal() {
        GraphQLError unclassified = new GraphQLError() {
            @Override
            public String getMessage() {
                return "low-level detail";
            }

            @Override
            public List<SourceLocation> getLocations() {
                return List.of();
            }

            @Override
            public ErrorClassification getErrorType() {
                return null;
            }
        };

        GraphQLError normalized = normalizer.normalize(unclassified);

        assertThat(codeOf(normalized)).isEqualTo("INTERNAL");
        assertThat(normalized.getMessage()).isEqualTo(ErrorCode.INTERNAL.defaultMessage());
    }

    @Test
    void disabledIntrospectionIsABadRequestWithItsOriginalMessage() {
        GraphQLError normalized = normalizer.normalize(
            error(ErrorClassification.errorClassification("IntrospectionDisabled"), null));

        assertThat(codeOf(normalized)).isEqualTo("BAD_REQUEST");
        assertThat(normalized.getMessage()).isEqualTo("original message");
    }

    @Test
    void badFaithIntrospectionIsTooComplex() {
        GraphQLError normalized = normalizer.normalize(
            error(ErrorClassification.errorClassification("BadFaithIntrospection"), null));

        assertThat(codeOf(normalized)).isEqualTo("QUERY_TOO_COMPLEX");
    }

    @Test
    void keepsClientFacingMessagesForQueryProblems() {
        GraphQLError normalized = normalizer.normalize(error(graphql.ErrorType.ValidationError, null));

        assertThat(normalized.getMessage()).isEqualTo("original message");
        assertThat(normalized.getLocations()).containsExactly(new SourceLocation(1, 3));
        assertThat(normalized.getPath()).containsExactly("serverInfo");
    }

    @Test
    void replacesMessagesThatCouldLeakInternals() {
        GraphQLError normalized = normalizer.normalize(error(graphql.ErrorType.DataFetchingException, null));

        assertThat(normalized.getMessage()).isEqualTo(ErrorCode.INTERNAL.defaultMessage());
    }

    @Test
    void leavesErrorsThatAlreadyHaveACodeUntouched() {
        GraphQLError coded = error(ErrorType.NOT_FOUND, Map.of("code", "NOT_FOUND"));

        assertThat(normalizer.normalize(coded)).isSameAs(coded);
    }

    @Test
    void keepsOtherExtensionsWhenAddingTheCode() {
        GraphQLError normalized = normalizer.normalize(
            error(graphql.ErrorType.ExecutionAborted, Map.of("maxDepth", 12, "code", 42)));

        assertThat(normalized.getExtensions()).containsEntry("maxDepth", 12).containsEntry("code", "QUERY_TOO_COMPLEX");
    }

    private static GraphQLError error(ErrorClassification type, Map<String, Object> extensions) {
        return GraphqlErrorBuilder.newError()
            .message("original message")
            .errorType(type)
            .locations(List.of(new SourceLocation(1, 3)))
            .path(List.of("serverInfo"))
            .extensions(extensions)
            .build();
    }

    private static String codeOf(GraphQLError error) {
        return (String) error.getExtensions().get("code");
    }
}
