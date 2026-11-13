package com.aurelle.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelle.common.ApplicationException;
import com.aurelle.common.ErrorCode;
import graphql.GraphQLError;
import graphql.Scalars;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class GraphQlExceptionResolverTest {

    private final GraphQlExceptionResolver resolver = new GraphQlExceptionResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void applicationExceptionsKeepTheirCodeAndSafeMessage() {
        GraphQLError error = resolve(new ApplicationException(ErrorCode.NOT_FOUND, "That order does not exist."));

        assertThat(error.getMessage()).isEqualTo("That order does not exist.");
        assertThat(error.getExtensions()).containsEntry("code", "NOT_FOUND").doesNotContainKey("correlationId");
        assertThat(error.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(error.getPath()).containsExactly("shopSettings");
    }

    @Test
    void authenticationFailuresAreUnauthenticated() {
        assertThat(code(new BadCredentialsException("bad"))).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void accessDeniedForAnonymousCallersIsUnauthenticated() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(code(new AccessDeniedException("no"))).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void accessDeniedWithoutAnyAuthenticationIsUnauthenticated() {
        assertThat(code(new AccessDeniedException("no"))).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void accessDeniedForSignedInCallersIsForbidden() {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("staff", null, List.of()));

        GraphQLError error = resolve(new AccessDeniedException("internal detail"));

        assertThat(error.getExtensions()).containsEntry("code", "FORBIDDEN");
        assertThat(error.getMessage()).isEqualTo(ErrorCode.FORBIDDEN.defaultMessage()).doesNotContain("internal");
    }

    @Test
    void dataAccessFailuresMapToStableCodes() {
        assertThat(code(new OptimisticLockingFailureException("stale"))).isEqualTo("CONFLICT");
        assertThat(code(new QueryTimeoutException("slow"))).isEqualTo("TIMEOUT");
        assertThat(code(new DataAccessResourceFailureException("db down"))).isEqualTo("UNAVAILABLE");
    }

    @Test
    void constraintViolationsAreBadRequests() {
        assertThat(code(new ConstraintViolationException("invalid", Set.of()))).isEqualTo("BAD_REQUEST");
    }

    @Test
    void unexpectedExceptionsAreInternalWithCorrelationIdAndNoDetails() {
        GraphQLError error = resolve(new IllegalStateException("SELECT * FROM secrets failed"));

        assertThat(error.getExtensions()).containsEntry("code", "INTERNAL").containsKey("correlationId");
        assertThat(error.getMessage()).isEqualTo(ErrorCode.INTERNAL.defaultMessage()).doesNotContain("SELECT");
        assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
    }

    @Test
    void otherTransientDataErrorsAreInternal() {
        assertThat(code(new CannotAcquireLockException("lock"))).isEqualTo("INTERNAL");
    }

    private String code(Throwable ex) {
        return (String) resolve(ex).getExtensions().get("code");
    }

    private GraphQLError resolve(Throwable ex) {
        List<GraphQLError> errors = resolver.resolveException(ex, environment()).block();
        assertThat(errors).hasSize(1);
        return errors.getFirst();
    }

    private static DataFetchingEnvironment environment() {
        Field field = Field.newField("shopSettings").build();
        return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
            .mergedField(MergedField.newMergedField(field).build())
            .executionStepInfo(ExecutionStepInfo.newExecutionStepInfo()
                .type(Scalars.GraphQLString)
                .path(ResultPath.rootPath().segment("shopSettings"))
                .build())
            .build();
    }
}
