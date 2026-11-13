package com.aurelle.messaging.internal;

import com.aurelle.messaging.EmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Chooses the e-mail adapter from {@code aurelle.mail.enabled}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MailProperties.class)
class MailConfiguration {

    @Bean
    EmailSender emailSender(MailProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        if (!properties.enabled()) {
            return new DisabledEmailSender();
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException(
                "aurelle.mail.enabled=true but no SMTP host is configured: set AURELLE_MAIL_HOST");
        }
        return new SmtpEmailSender(sender, properties.from(), properties.fromName());
    }
}
