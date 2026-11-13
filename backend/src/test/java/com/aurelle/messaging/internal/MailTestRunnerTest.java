package com.aurelle.messaging.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelle.messaging.EmailMessage;
import com.aurelle.messaging.EmailSender;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class MailTestRunnerTest {

    private final List<EmailMessage> sent = new ArrayList<>();
    private final EmailSender capture = sent::add;

    @Test
    void doesNothingWithoutATestRecipient() {
        runner(null).run(new DefaultApplicationArguments());
        runner("  ").run(new DefaultApplicationArguments());

        assertThat(sent).isEmpty();
    }

    @Test
    void sendsOneSampleVerificationEmailToTheTestRecipient() {
        runner("tester@example.test").run(new DefaultApplicationArguments());

        assertThat(sent).hasSize(1);
        EmailMessage message = sent.getFirst();
        assertThat(message.to()).isEqualTo("tester@example.test");
        assertThat(message.subject()).isEqualTo("Confirm your e-mail · Aurelle");
        assertThat(message.textBody()).containsPattern("\\d{6}").contains("http://localhost:8081/verify-email?token=");
    }

    @Test
    void springConstructorUsesASecureRandom() {
        var runner = new MailTestRunner(capture, properties("tester@example.test"), "http://localhost:8081");
        runner.run(new DefaultApplicationArguments());

        assertThat(sent).hasSize(1);
    }

    private MailTestRunner runner(String recipient) {
        return new MailTestRunner(capture, properties(recipient), "http://localhost:8081", new SecureRandom());
    }

    private static MailProperties properties(String recipient) {
        return new MailProperties(true, "no-reply@example.test", "Aurelle", recipient);
    }
}
