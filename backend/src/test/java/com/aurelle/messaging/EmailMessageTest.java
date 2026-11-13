package com.aurelle.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailMessageTest {

    @Test
    void masksTheRecipientAndNeverPrintsTheBody() {
        var message = new EmailMessage("amina@example.test", "Confirm your e-mail", "code 123456", null);

        assertThat(message.maskedRecipient()).isEqualTo("a***@example.test");
        assertThat(message.toString()).contains("a***@example.test").doesNotContain("amina").doesNotContain("123456");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-address", "a@b", "two@@example.test", "spa ce@example.test"})
    void rejectsInvalidAddresses(String to) {
        assertThatThrownBy(() -> new EmailMessage(to, "Subject", "Body", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "Line\nInjected: header", "Carriage\rreturn"})
    void rejectsBlankOrMultiLineSubjects(String subject) {
        assertThatThrownBy(() -> new EmailMessage("a@example.test", subject, "Body", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresRecipientSubjectAndText() {
        assertThatThrownBy(() -> new EmailMessage(null, "S", "B", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmailMessage("a@example.test", null, "B", null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmailMessage("a@example.test", "S", null, null))
            .isInstanceOf(NullPointerException.class);
    }
}
