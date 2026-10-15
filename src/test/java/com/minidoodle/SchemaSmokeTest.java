package com.minidoodle;

import com.minidoodle.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Flyway-managed schema is present and carries the overlap-prevention
 * exclusion constraint.
 */
class SchemaSmokeTest extends PostgresIntegrationTest {

    @Test
    void migrationsCreateExpectedTables() {
        List<String> tables = dsl.fetch(
                "select table_name::text from information_schema.tables "
                        + "where table_schema = 'public' and table_type = 'BASE TABLE'")
                .getValues(0, String.class);

        assertThat(tables).contains("users", "calendars", "time_slots");
    }

    @Test
    void overlapExclusionConstraintExists() {
        Integer count = dsl.fetchOne(
                "select count(*)::int as count from pg_constraint where conname = 'ex_slot_no_overlap'")
                .get("count", Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
