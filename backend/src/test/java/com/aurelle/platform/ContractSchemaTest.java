package com.aurelle.platform;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks every file in contracts/graphql, including phases not served yet, so contract mistakes
 * are caught while a phase is in draft/review, before the frontend generates types from it.
 */
class ContractSchemaTest {

    private static final Path CONTRACTS = Path.of("..", "contracts");
    private static final Path GRAPHQL = CONTRACTS.resolve("graphql");
    private static final Path PERMISSION_CATALOGUE = CONTRACTS.resolve("data-model").resolve("01-identity-access.md");
    /** Character classes only (no repeated groups): linear matching, no deep recursion. */
    private static final Pattern REQUIRED_PERMISSION = Pattern.compile("Requires permission: ([a-z][a-z_.]*[a-z_])");
    private static final Pattern CATALOGUE_KEY = Pattern.compile("`([a-z][a-z_.]*[a-z_])`");

    private static List<Path> files;
    private static GraphQLSchema schema;

    @BeforeAll
    static void parseAllContractFiles() throws IOException {
        try (Stream<Path> stream = Files.list(GRAPHQL)) {
            files = stream.filter(p -> p.toString().endsWith(".graphqls")).sorted().toList();
        }
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
        SchemaParser parser = new SchemaParser();
        for (Path file : files) {
            registry.merge(parser.parse(Files.readString(file)));
        }
        schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
    }

    @Test
    void allContractFilesFormOneValidSchema() {
        assertThat(files).as("contract files in %s", GRAPHQL.toAbsolutePath()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(schema.getQueryType().getFieldDefinition("serverInfo")).isNotNull();
        assertThat(schema.getQueryType().getFieldDefinition("viewer")).isNotNull();
        assertThat(schema.getMutationType().getFieldDefinition("signIn")).isNotNull();
    }

    @Test
    void mutationsTakeAtMostASingleInputArgument() {
        List<String> violations = schema.getMutationType().getFieldDefinitions().stream()
            .filter(ContractSchemaTest::violatesSingleInput)
            .map(GraphQLFieldDefinition::getName)
            .toList();

        assertThat(violations).as("mutations not using a single `input` argument (contracts.md)").isEmpty();
    }

    @Test
    void everyRequiredPermissionIsInTheCatalogue() throws IOException {
        Set<String> catalogue = matches(CATALOGUE_KEY, Files.readString(PERMISSION_CATALOGUE));
        Set<String> required = new TreeSet<>();
        for (Path file : files) {
            required.addAll(matches(REQUIRED_PERMISSION, Files.readString(file)));
        }

        assertThat(required).as("permission keys used in SDL descriptions").isNotEmpty();
        assertThat(catalogue).as("permission catalogue in %s", PERMISSION_CATALOGUE).containsAll(required);
    }

    private static boolean violatesSingleInput(GraphQLFieldDefinition field) {
        return field.getArguments().size() > 1
            || field.getArguments().stream().anyMatch(argument -> !"input".equals(argument.getName()));
    }

    private static Set<String> matches(Pattern pattern, String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key.chars().filter(c -> c == '.').count() == 2 && !key.contains("..")) {
                found.add(key);
            }
        }
        return found;
    }
}
