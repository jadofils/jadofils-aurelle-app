package com.aurelle.common;

import java.util.List;
import java.util.Objects;

/**
 * Expected business failure returned inside a mutation payload (GraphQL {@code UserError}).
 *
 * @param code    stable code from contracts/errors.md, e.g. {@code EMAIL_TAKEN}
 * @param message safe, human-readable message that says how to fix the problem
 * @param field   path to the input field at fault, e.g. ["input", "email"], or empty
 */
public record UserError(String code, String message, List<String> field) {

    public UserError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        field = field == null ? List.of() : List.copyOf(field);
    }

    public static UserError of(String code, String message, String... inputPath) {
        return new UserError(code, message, inputPath.length == 0 ? List.of() : List.of(inputPath));
    }
}
