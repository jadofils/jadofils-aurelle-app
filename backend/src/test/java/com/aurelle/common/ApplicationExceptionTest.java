package com.aurelle.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApplicationExceptionTest {

    @Test
    void usesTheCodesDefaultMessage() {
        var ex = new ApplicationException(ErrorCode.CONFLICT);

        assertThat(ex.code()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo(ErrorCode.CONFLICT.defaultMessage());
    }

    @Test
    void acceptsASpecificSafeMessage() {
        var ex = new ApplicationException(ErrorCode.NOT_FOUND, "That product is no longer available.");

        assertThat(ex.getMessage()).isEqualTo("That product is no longer available.");
    }

    @Test
    void requiresCodeAndMessage() {
        assertThatThrownBy(() -> new ApplicationException(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApplicationException(ErrorCode.INTERNAL, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void everyCodeHasAUserFacingDefaultMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.defaultMessage()).as(code.name()).isNotBlank().endsWith(".");
        }
    }
}
