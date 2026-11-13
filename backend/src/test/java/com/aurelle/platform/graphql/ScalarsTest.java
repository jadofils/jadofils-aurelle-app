package com.aurelle.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.BooleanValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScalarsTest {

    private static final GraphQLContext CTX = GraphQLContext.getDefault();
    private static final Locale LOCALE = Locale.ROOT;
    private static final CoercedVariables NO_VARS = CoercedVariables.emptyVariables();

    @Test
    void scalarNamesMatchTheContract() {
        assertThat(Scalars.DATE_TIME.getName()).isEqualTo("DateTime");
        assertThat(Scalars.DATE.getName()).isEqualTo("Date");
        assertThat(Scalars.LONG.getName()).isEqualTo("Long");
    }

    @Nested
    class DateTimeScalar {

        @SuppressWarnings("unchecked")
        private final Coercing<Instant, String> coercing =
            (Coercing<Instant, String>) Scalars.DATE_TIME.getCoercing();
        private final Instant instant = Instant.parse("2026-11-13T09:30:00Z");

        @Test
        void serializesInstantsAndZonedValuesInUtc() {
            assertThat(coercing.serialize(instant, CTX, LOCALE)).isEqualTo("2026-11-13T09:30:00Z");
            assertThat(coercing.serialize(OffsetDateTime.of(2026, 11, 13, 11, 30, 0, 0, ZoneOffset.ofHours(2)), CTX,
                LOCALE)).isEqualTo("2026-11-13T09:30:00Z");
            assertThat(coercing.serialize(ZonedDateTime.of(2026, 11, 13, 11, 30, 0, 0, ZoneId.of("Africa/Kigali")), CTX,
                LOCALE)).isEqualTo("2026-11-13T09:30:00Z");
        }

        @Test
        void rejectsOtherTypesOnSerialize() {
            assertThatThrownBy(() -> coercing.serialize("2026-11-13", CTX, LOCALE))
                .isInstanceOf(CoercingSerializeException.class);
            assertThatThrownBy(() -> coercing.serialize(null, CTX, LOCALE))
                .isInstanceOf(CoercingSerializeException.class).hasMessageContaining("null");
        }

        @Test
        void parsesValuesWithAnyOffset() {
            assertThat(coercing.parseValue("2026-11-13T11:30:00+02:00", CTX, LOCALE)).isEqualTo(instant);
        }

        @Test
        void rejectsInvalidValues() {
            assertThatThrownBy(() -> coercing.parseValue("2026-11-13", CTX, LOCALE))
                .isInstanceOf(CoercingParseValueException.class);
            assertThatThrownBy(() -> coercing.parseValue(42, CTX, LOCALE))
                .isInstanceOf(CoercingParseValueException.class);
        }

        @Test
        void parsesStringLiterals() {
            assertThat(coercing.parseLiteral(new StringValue("2026-11-13T09:30:00Z"), NO_VARS, CTX, LOCALE))
                .isEqualTo(instant);
        }

        @Test
        void rejectsInvalidLiterals() {
            assertThatThrownBy(() -> coercing.parseLiteral(new StringValue("yesterday"), NO_VARS, CTX, LOCALE))
                .isInstanceOf(CoercingParseLiteralException.class);
            assertThatThrownBy(() -> coercing.parseLiteral(new BooleanValue(true), NO_VARS, CTX, LOCALE))
                .isInstanceOf(CoercingParseLiteralException.class);
        }
    }

    @Nested
    class DateScalar {

        @SuppressWarnings("unchecked")
        private final Coercing<LocalDate, String> coercing =
            (Coercing<LocalDate, String>) Scalars.DATE.getCoercing();
        private final LocalDate date = LocalDate.of(2026, 11, 13);

        @Test
        void serializesLocalDates() {
            assertThat(coercing.serialize(date, CTX, LOCALE)).isEqualTo("2026-11-13");
            assertThatThrownBy(() -> coercing.serialize(Instant.EPOCH, CTX, LOCALE))
                .isInstanceOf(CoercingSerializeException.class);
        }

        @Test
        void parsesValues() {
            assertThat(coercing.parseValue("2026-11-13", CTX, LOCALE)).isEqualTo(date);
            assertThatThrownBy(() -> coercing.parseValue("13/11/2026", CTX, LOCALE))
                .isInstanceOf(CoercingParseValueException.class);
            assertThatThrownBy(() -> coercing.parseValue(20261113, CTX, LOCALE))
                .isInstanceOf(CoercingParseValueException.class);
        }

        @Test
        void parsesLiterals() {
            assertThat(coercing.parseLiteral(new StringValue("2026-11-13"), NO_VARS, CTX, LOCALE)).isEqualTo(date);
            assertThatThrownBy(() -> coercing.parseLiteral(new StringValue("2026-13-01"), NO_VARS, CTX, LOCALE))
                .isInstanceOf(CoercingParseLiteralException.class);
            assertThatThrownBy(() -> coercing.parseLiteral(new IntValue(BigInteger.ONE), NO_VARS, CTX, LOCALE))
                .isInstanceOf(CoercingParseLiteralException.class);
        }
    }

    @Nested
    class LongScalar {

        @SuppressWarnings("unchecked")
        private final Coercing<Long, Long> coercing = (Coercing<Long, Long>) Scalars.LONG.getCoercing();
        private final BigInteger tooBig = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        private final BigInteger tooSmall = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE);

        @Test
        void serializesWholeNumbers() {
            assertThat(coercing.serialize(15_000L, CTX, LOCALE)).isEqualTo(15_000L);
            assertThat(coercing.serialize(7, CTX, LOCALE)).isEqualTo(7L);
            assertThat(coercing.serialize((short) 3, CTX, LOCALE)).isEqualTo(3L);
            assertThat(coercing.serialize((byte) 1, CTX, LOCALE)).isEqualTo(1L);
            assertThat(coercing.serialize(BigInteger.TEN, CTX, LOCALE)).isEqualTo(10L);
        }

        @Test
        void rejectsFractionsAndOverflowOnSerialize() {
            assertThatThrownBy(() -> coercing.serialize(1.5d, CTX, LOCALE))
                .isInstanceOf(CoercingSerializeException.class);
            assertThatThrownBy(() -> coercing.serialize(tooBig, CTX, LOCALE))
                .isInstanceOf(CoercingSerializeException.class);
        }

        @Test
        void parsesWholeNumberValues() {
            assertThat(coercing.parseValue(Long.MAX_VALUE, CTX, LOCALE)).isEqualTo(Long.MAX_VALUE);
            assertThat(coercing.parseValue(42, CTX, LOCALE)).isEqualTo(42L);
            assertThat(coercing.parseValue((short) 2, CTX, LOCALE)).isEqualTo(2L);
            assertThat(coercing.parseValue((byte) 2, CTX, LOCALE)).isEqualTo(2L);
            assertThat(coercing.parseValue(BigInteger.valueOf(Long.MIN_VALUE), CTX, LOCALE)).isEqualTo(Long.MIN_VALUE);
        }

        @Test
        void rejectsInvalidValues() {
            assertThatThrownBy(() -> coercing.parseValue("42", CTX, LOCALE))
                .isInstanceOf(CoercingParseValueException.class);
            assertThatThrownBy(() -> coercing.parseValue(tooBig, CTX, LOCALE))
                .isInstanceOf(CoercingParseValueException.class);
            assertThatThrownBy(() -> coercing.parseValue(tooSmall, CTX, LOCALE))
                .isInstanceOf(CoercingParseValueException.class);
        }

        @Test
        void parsesIntLiteralsWithinRange() {
            assertThat(coercing.parseLiteral(new IntValue(BigInteger.valueOf(15_000)), NO_VARS, CTX, LOCALE))
                .isEqualTo(15_000L);
            assertThatThrownBy(() -> coercing.parseLiteral(new IntValue(tooBig), NO_VARS, CTX, LOCALE))
                .isInstanceOf(CoercingParseLiteralException.class);
            assertThatThrownBy(() -> coercing.parseLiteral(new StringValue("1"), NO_VARS, CTX, LOCALE))
                .isInstanceOf(CoercingParseLiteralException.class);
        }
    }
}
