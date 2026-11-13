package com.aurelle.messaging.internal;

import com.aurelle.messaging.EmailDeliveryException;
import com.aurelle.messaging.EmailMessage;
import com.aurelle.messaging.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Sends e-mail over SMTP (e.g. Gmail with an app password). Logs metadata only, never bodies. */
class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;

    SmtpEmailSender(JavaMailSender mailSender, String from, String fromName) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, message.htmlBody() != null,
                StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            if (message.htmlBody() != null) {
                helper.setText(message.textBody(), message.htmlBody());
            } else {
                helper.setText(message.textBody());
            }
            mailSender.send(mime);
            log.atInfo().addKeyValue("to", message.maskedRecipient()).log("E-mail sent");
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            log.atWarn().addKeyValue("to", message.maskedRecipient()).log("E-mail delivery failed");
            throw new EmailDeliveryException("E-mail delivery failed", ex);
        }
    }
}
