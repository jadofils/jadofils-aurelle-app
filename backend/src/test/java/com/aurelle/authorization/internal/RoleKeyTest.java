package com.aurelle.authorization.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RoleKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"owner", "weekend-lead", "a", "shift-2-lead", "x0"})
    void acceptsLowercaseKeysWithInnerDashes(String key) {
        assertThat(JdbcRoleAdministration.isRoleKey(key)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"-lead", "lead-", "double--dash", "Bad Key", "UPPER", "under_score", "émoji"})
    void rejectsEverythingElse(String key) {
        assertThat(JdbcRoleAdministration.isRoleKey(key)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void rejectsKeysLongerThanFortyCharacters() {
        assertThat(JdbcRoleAdministration.isRoleKey("a".repeat(40))).isTrue();
        assertThat(JdbcRoleAdministration.isRoleKey("a".repeat(41))).isFalse();
        assertThat(JdbcRoleAdministration.isRoleKey("a-".repeat(30_000) + "a")).isFalse();
    }
}
