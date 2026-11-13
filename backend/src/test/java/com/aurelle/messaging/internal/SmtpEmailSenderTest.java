package com.aurelle.messaging.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelle.messaging.EmailDeliveryException;
import com.aurelle.messaging.EmailMessage;
import com.aurelle.messaging.VerificationEmails;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Sends through a real SMTP conversation with an in-JVM GreenMail server. */
class SmtpEmailSenderTest {

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    @Test
    void sendsTextAndHtmlFromTheConfiguredSender() throws Exception {
        sender(SMTP.getSmtp().getPort()).send(VerificationEmails.compose(VerificationEmails.Purpose.EMAIL_VERIFY,
            "Aurelle", "amina@example.test", "Amina", "042917", "https://shop.example.test/v?token=t",
            Duration.ofHours(24)));

        MimeMessage[] received = SMTP.getReceivedMessages();
        assertThat(received).hasSize(1);
        MimeMessage mail = received[0];
        assertThat(mail.getSubject()).isEqualTo("Confirm your e-mail · Aurelle");
        assertThat(((InternetAddress) mail.getFrom()[0]).getAddress()).isEqualTo("no-reply@example.test");
        assertThat(((InternetAddress) mail.getFrom()[0]).getPersonal()).isEqualTo("Aurelle");
        assertThat(mail.getAllRecipients()[0]).hasToString("amina@example.test");
        assertThat(GreenMailUtil.getBody(mail)).contains("042917").contains("text/html").contains("text/plain");
    }

    @Test
    void sendsTextOnlyMessages() throws Exception {
        sender(SMTP.getSmtp().getPort()).send(new EmailMessage("kwame@example.test", "Plain", "Just text", null));

        MimeMessage mail = SMTP.getReceivedMessages()[0];
        assertThat(mail.getContentType()).startsWith("text/plain");
        assertThat(GreenMailUtil.getBody(mail)).contains("Just text");
    }

    @Test
    void wrapsDeliveryFailures() {
        int closedPort = SMTP.getSmtp().getPort() + 1;
        SmtpEmailSender unreachable = sender(closedPort);

        assertThatThrownBy(() -> unreachable.send(new EmailMessage("a@example.test", "S", "B", null)))
            .isInstanceOf(EmailDeliveryException.class)
            .hasMessage("E-mail delivery failed");
    }

    private static SmtpEmailSender sender(int port) {
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost("127.0.0.1");
        mail.setPort(port);
        mail.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
        mail.getJavaMailProperties().put("mail.smtp.timeout", "2000");
        return new SmtpEmailSender(mail, "no-reply@example.test", "Aurelle");
    }
}
