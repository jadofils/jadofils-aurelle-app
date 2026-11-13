package com.aurelle.messaging.internal;

import com.aurelle.messaging.EmailSender;
import com.aurelle.messaging.VerificationEmails;
import java.security.SecureRandom;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev only: with {@code --aurelle.mail.test-recipient=you@example.com}, sends one sample
 * verification e-mail at startup so the SMTP settings in {@code .env} can be checked end to end.
 * The sample code is random and not stored anywhere; it verifies nothing.
 */
@Component
@Profile("dev")
class MailTestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MailTestRunner.class);

    private final EmailSender emailSender;
    private final MailProperties properties;
    private final String publicWebUrl;
    private final SecureRandom random;

    @Autowired
    MailTestRunner(EmailSender emailSender, MailProperties properties,
            @Value("${aurelle.web.public-url}") String publicWebUrl) {
        this(emailSender, properties, publicWebUrl, new SecureRandom());
    }

    MailTestRunner(EmailSender emailSender, MailProperties properties, String publicWebUrl, SecureRandom random) {
        this.emailSender = emailSender;
        this.properties = properties;
        this.publicWebUrl = publicWebUrl;
        this.random = random;
    }

    @Override
    public void run(ApplicationArguments args) {
        String recipient = properties.testRecipient();
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        String code = "%06d".formatted(random.nextInt(1_000_000));
        emailSender.send(VerificationEmails.compose(VerificationEmails.Purpose.EMAIL_VERIFY, "Aurelle", recipient,
            "Aurelle tester", code, publicWebUrl + "/verify-email?token=sample", Duration.ofHours(24)));
        log.info("Sample verification e-mail handed to the mail sender (mail enabled: {})", properties.enabled());
    }
}
