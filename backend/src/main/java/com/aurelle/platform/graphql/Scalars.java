package com.aurelle.platform.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Custom scalars declared in {@code contracts/graphql/00-common.graphqls}.
 *
 * <p>Implemented here rather than via graphql-java-extended-scalars because that library has no
 * release for graphql-java 25 (the version managed by Spring Boot 4.1).
 */
final class Scalars {

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private Scalars() {
    }

    /** {@code DateTime}: an instant, always written as ISO-8601 in UTC ({@code 2026-11-13T09:30:00Z}). */
    static final GraphQLScalarType DATE_TIME = GraphQLScalarType.newScalar()
        .name("DateTime")
        .description("ISO-8601 instant in UTC")
        .coercing(new DateTimeCoercing())
        .build();

    /** {@code Date}: an ISO-8601 calendar date ({@code 2026-11-13}). */
    static final GraphQLScalarType DATE = GraphQLScalarType.newScalar()
        .name("Date")
        .description("ISO-8601 calendar date")
        .coercing(new DateCoercing())
        .build();

    /** {@code Long}: a 64-bit signed integer (money in minor units, counts). */
    static final GraphQLScalarType LONG = GraphQLScalarType.newScalar()
        .name("Long")
        .description("64-bit signed integer")
        .coercing(new LongCoercing())
        .build();

    static final class DateTimeCoercing implements Coercing<Instant, String> {

        @Override
        public String serialize(Object value, GraphQLContext context, Locale locale) {
            if (value instanceof Instant instant) {
                return instant.toString();
            }
            if (value instanceof OffsetDateTime offset) {
                return offset.toInstant().toString();
            }
            if (value instanceof ZonedDateTime zoned) {
                return zoned.toInstant().toString();
            }
            throw new CoercingSerializeException("DateTime cannot serialize " + typeName(value));
        }

        @Override
        public Instant parseValue(Object input, GraphQLContext context, Locale locale) {
            if (input instanceof String text) {
                return parse(text, CoercingParseValueException::new);
            }
            throw new CoercingParseValueException("DateTime must be an ISO-8601 string");
        }

        @Override
        public Instant parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context,
                Locale locale) {
            if (input instanceof StringValue text) {
                return parse(text.getValue(), CoercingParseLiteralException::new);
            }
            throw new CoercingParseLiteralException("DateTime must be an ISO-8601 string");
        }

        private static <E extends RuntimeException> Instant parse(String text,
                java.util.function.Function<String, E> error) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeException ex) {
                throw error.apply("DateTime must be ISO-8601 with an offset, e.g. 2026-11-13T09:30:00Z");
            }
        }
    }

    static final class DateCoercing implements Coercing<LocalDate, String> {

        @Override
        public String serialize(Object value, GraphQLContext context, Locale locale) {
            if (value instanceof LocalDate date) {
                return date.toString();
            }
            throw new CoercingSerializeException("Date cannot serialize " + typeName(value));
        }

        @Override
        public LocalDate parseValue(Object input, GraphQLContext context, Locale locale) {
            if (input instanceof String text) {
                return parse(text, CoercingParseValueException::new);
            }
            throw new CoercingParseValueException("Date must be an ISO-8601 string");
        }

        @Override
        public LocalDate parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context,
                Locale locale) {
            if (input instanceof StringValue text) {
                return parse(text.getValue(), CoercingParseLiteralException::new);
            }
            throw new CoercingParseLiteralException("Date must be an ISO-8601 string");
        }

        private static <E extends RuntimeException> LocalDate parse(String text,
                java.util.function.Function<String, E> error) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeException ex) {
                throw error.apply("Date must be ISO-8601, e.g. 2026-11-13");
            }
        }
    }

    static final class LongCoercing implements Coercing<Long, Long> {

        @Override
        public Long serialize(Object value, GraphQLContext context, Locale locale) {
            if (value instanceof Long number) {
                return number;
            }
            if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
                return ((Number) value).longValue();
            }
            if (value instanceof BigInteger big && fitsInLong(big)) {
                return big.longValue();
            }
            throw new CoercingSerializeException("Long cannot serialize " + typeName(value));
        }

        @Override
        public Long parseValue(Object input, GraphQLContext context, Locale locale) {
            if (input instanceof Long number) {
                return number;
            }
            if (input instanceof Integer || input instanceof Short || input instanceof Byte) {
                return ((Number) input).longValue();
            }
            if (input instanceof BigInteger big && fitsInLong(big)) {
                return big.longValue();
            }
            throw new CoercingParseValueException("Long must be a whole number within 64-bit range");
        }

        @Override
        public Long parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context,
                Locale locale) {
            if (input instanceof IntValue integer && fitsInLong(integer.getValue())) {
                return integer.getValue().longValue();
            }
            throw new CoercingParseLiteralException("Long must be a whole number within 64-bit range");
        }

        private static boolean fitsInLong(BigInteger value) {
            return value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0;
        }
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
