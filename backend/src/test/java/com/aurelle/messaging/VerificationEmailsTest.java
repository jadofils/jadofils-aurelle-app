package com.aurelle.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelle.messaging.VerificationEmails.Purpose;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class VerificationEmailsTest {

    private static final String LINK = "https://shop.example.test/verify-email?token=abc";

    @Test
    void containsCodeAndLinkInBothBodiesButNotInTheSubject() {
        EmailMessage message = VerificationEmails.compose(Purpose.EMAIL_VERIFY, "Aurelle", "amina@example.test",
            "Amina", "042917", LINK, Duration.ofHours(24));

        assertThat(message.subject()).isEqualTo("Confirm your e-mail · Aurelle").doesNotContain("042917");
        assertThat(message.textBody()).contains("Hi Amina,", "042917", LINK, "expire in 24 hours");
        assertThat(message.htmlBody()).contains("042917", "href=\"" + LINK + "\"", "Confirm e-mail", "#ff7200");
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void everyPurposeHasItsOwnWording(Purpose purpose) {
        EmailMessage message = VerificationEmails.compose(purpose, "Aurelle", "a@example.test", "A", "123456",
            LINK, Duration.ofMinutes(30));

        assertThat(message.subject()).endsWith("· Aurelle");
        assertThat(message.textBody()).contains("30 minutes");
    }

    @Test
    void escapesNamesInHtml() {
        EmailMessage message = VerificationEmails.compose(Purpose.PASSWORD_RESET, "Aurelle & Co",
            "a@example.test", "<script>alert(1)</script>", "123456", LINK, Duration.ofMinutes(30));

        assertThat(message.htmlBody()).doesNotContain("<script>").contains("&lt;script&gt;", "Aurelle &amp; Co");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "12a456", ""})
    void rejectsCodesThatAreNotSixDigits(String code) {
        assertThatThrownBy(() -> VerificationEmails.compose(Purpose.EMAIL_VERIFY, "Aurelle", "a@example.test", "A",
            code, LINK, Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingCode() {
        assertThatThrownBy(() -> VerificationEmails.compose(Purpose.EMAIL_VERIFY, "Aurelle", "a@example.test", "A",
            null, LINK, Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://x.example.test", "/relative"})
    void rejectsNonHttpLinks(String link) {
        assertThatThrownBy(() -> VerificationEmails.compose(Purpose.EMAIL_VERIFY, "Aurelle", "a@example.test", "A",
            "123456", link, Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingLink() {
        assertThatThrownBy(() -> VerificationEmails.compose(Purpose.EMAIL_VERIFY, "Aurelle", "a@example.test", "A",
            "123456", null, Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsPlainHttpLinksForLocalDevelopment() {
        assertThat(VerificationEmails.compose(Purpose.EMAIL_VERIFY, "Aurelle", "a@example.test", "A", "123456",
            "http://localhost:8081/verify-email?token=x", Duration.ofHours(1)).textBody()).contains("localhost");
    }

    @ParameterizedTest
    @CsvSource({"1, 1 minute", "30, 30 minutes", "60, 1 hour", "180, 3 hours", "1440, 24 hours", "4320, 3 days"})
    void describesExpiry(long minutes, String expected) {
        assertThat(VerificationEmails.describe(Duration.ofMinutes(minutes))).isEqualTo(expected);
    }

    @Test
    void rejectsExpiryUnderOneMinute() {
        assertThatThrownBy(() -> VerificationEmails.describe(Duration.ofSeconds(30)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
