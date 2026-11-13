package com.aurelle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Owner rule (.claude/rules/data-model.md, "Test seed data"): with the seed profile every
 * business table holds at least 4 rows. New tables are picked up automatically.
 */
class SeedDataIT extends IntegrationTest {

    private static final int MINIMUM_ROWS = 4;
    private static final List<String> NOT_BUSINESS_TABLES = List.of("flyway_schema_history");

    @Autowired
    JdbcClient jdbc;

    @Test
    void everyBusinessTableHasAtLeastFourRows() {
        List<String> tables = jdbc.sql("""
                select table_name from information_schema.tables
                 where table_schema = current_schema() and table_type = 'BASE TABLE'
                 order by table_name
                """).query(String.class).list();
        assertThat(tables).as("tables in schema").isNotEmpty();

        Map<String, Long> tooSmall = new TreeMap<>();
        for (String table : tables) {
            if (NOT_BUSINESS_TABLES.contains(table)) {
                continue;
            }
            long rows = jdbc.sql("select count(*) from \"" + table.replace("\"", "\"\"") + "\"")
                .query(Long.class).single();
            if (rows < MINIMUM_ROWS) {
                tooSmall.put(table, rows);
            }
        }
        assertThat(tooSmall).as("tables with fewer than %d seed rows", MINIMUM_ROWS).isEmpty();
    }

    @Test
    void seedCoversRequiredOnlyAllFieldsAndArchivedRows() {
        assertThat(jdbc.sql("select count(*) from organization where archived_at is not null")
            .query(Long.class).single()).isPositive();
        assertThat(jdbc.sql("select count(*) from organization where tax_id is null and support_email is null")
            .query(Long.class).single()).isPositive();
        assertThat(jdbc.sql("select count(*) from shop_setting where free_shipping_threshold_minor is null")
            .query(Long.class).single()).isPositive();
        assertThat(jdbc.sql("select count(*) from shop_setting where free_shipping_threshold_minor = 0")
            .query(Long.class).single()).isPositive();
    }

    @Test
    void seedContactsAreFake() {
        List<String> emails = jdbc.sql("select support_email from organization where support_email is not null")
            .query(String.class).list();
        assertThat(emails).isNotEmpty().allSatisfy(email -> assertThat(email).endsWith("@example.test"));
    }
}
