package com.aurelle.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelle.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end GraphQL over HTTP: public queries, error codes and limits (contracts/errors.md). */
class GraphQlApiIT extends IntegrationTest {

    @Test
    void serverInfoIsPublic() {
        var response = graphql("{ serverInfo { apiVersion contractPhase } }");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> serverInfo = data(response, "serverInfo");
        assertThat((String) serverInfo.get("apiVersion")).isNotBlank().doesNotContain("@");
        assertThat(serverInfo).containsEntry("contractPhase", 0);
        assertThat(response.getBody()).doesNotContainKey("errors");
    }

    @Test
    void shopSettingsComeFromTheSeededDefaultShop() {
        var response = graphql("""
            { shopSettings {
                shopName currency currencyDisplaySymbol returnWindowDays tagline
                supportEmail supportPhone guestCheckoutEnabled
                freeShippingThreshold { amountMinor currency } } }
            """);

        Map<String, Object> settings = data(response, "shopSettings");
        assertThat(settings)
            .containsEntry("shopName", "Aurelle")
            .containsEntry("currency", "USD")
            .containsEntry("currencyDisplaySymbol", "$")
            .containsEntry("returnWindowDays", 30)
            .containsEntry("tagline", "Made to be worn on repeat.")
            .containsEntry("guestCheckoutEnabled", true)
            .containsEntry("supportEmail", "hello@example.test")
            .containsEntry("supportPhone", "+447700900001")
            .containsEntry("freeShippingThreshold", Map.of("amountMinor", 15000, "currency", "USD"));
    }

    @Test
    void syntaxErrorsReturnBadRequestCode() {
        var response = graphql("{ serverInfo { apiVersion ");

        assertThat(firstErrorCode(response)).isEqualTo("BAD_REQUEST");
    }

    @Test
    void unknownFieldsReturnBadRequestCode() {
        var response = graphql("{ serverInfo { secretField } }");

        assertThat(firstErrorCode(response)).isEqualTo("BAD_REQUEST");
        assertThat(firstError(response).get("message").toString()).contains("secretField");
    }

    @Test
    void tooDeepQueriesAreRejected() {
        // Introspection allows arbitrary nesting; 14 levels exceed the default max depth of 12.
        var response = graphql("""
            { __schema { types { fields { type { ofType { ofType { ofType { ofType {
              ofType { ofType { ofType { ofType { name } } } } } } } } } } } } }
            """);

        assertThat(firstErrorCode(response)).isEqualTo("QUERY_TOO_COMPLEX");
        assertThat(response.getBody()).extractingByKey("data").isNull();
    }

    @Test
    void tooComplexQueriesAreRejected() {
        // 260 aliases x 3 fields = 780 > the default max complexity of 500 (depth stays at 2).
        StringBuilder query = new StringBuilder("{");
        for (int i = 0; i < 260; i++) {
            query.append(" a").append(i).append(": serverInfo { apiVersion contractPhase }");
        }
        var response = graphql(query.append(" }").toString());

        assertThat(firstErrorCode(response)).isEqualTo("QUERY_TOO_COMPLEX");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> response, String field) {
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).as("data in %s", response.getBody()).isNotNull();
        return (Map<String, Object>) data.get(field);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstError(ResponseEntity<Map<String, Object>> response) {
        List<Map<String, Object>> errors = (List<Map<String, Object>>) response.getBody().get("errors");
        assertThat(errors).as("errors in %s", response.getBody()).isNotEmpty();
        return errors.getFirst();
    }

    @SuppressWarnings("unchecked")
    private static String firstErrorCode(ResponseEntity<Map<String, Object>> response) {
        Map<String, Object> extensions = (Map<String, Object>) firstError(response).get("extensions");
        return (String) extensions.get("code");
    }
}
