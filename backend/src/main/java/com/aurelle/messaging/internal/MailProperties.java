package com.aurelle.messaging.internal;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code aurelle.mail.*}. SMTP host and credentials are Spring's {@code spring.mail.*}, filled from
 * {@code AURELLE_MAIL_*} environment variables (or the git-ignored {@code .env} in the dev profile).
 *
 * @param enabled        send real e-mail; false drops messages (tests, CI, environments without SMTP)
 * @param from           sender address
 * @param fromName       sender display name
 * @param testRecipient  dev only: send one sample verification e-mail here at startup
 */
@Validated
@ConfigurationProperties("aurelle.mail")
record MailProperties(boolean enabled, @NotBlank String from, @NotBlank String fromName, String testRecipient) {
}
