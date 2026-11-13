package com.aurelle.messaging.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aurelle.messaging.EmailMessage;
import com.aurelle.messaging.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;

class MailConfigurationTest {

    private final MailConfiguration configuration = new MailConfiguration();

    @Test
    void disabledMailDropsMessagesWithoutTouchingSmtp() {
        JavaMailSender smtp = mock(JavaMailSender.class);

        EmailSender sender = configuration.emailSender(properties(false), provider(smtp));

        assertThat(sender).isInstanceOf(DisabledEmailSender.class);
        assertThatCode(() -> sender.send(new EmailMessage("a@example.test", "S", "B", null)))
            .doesNotThrowAnyException();
        verifyNoInteractions(smtp);
    }

    @Test
    void enabledMailUsesSmtp() {
        EmailSender sender = configuration.emailSender(properties(true), provider(mock(JavaMailSender.class)));

        assertThat(sender).isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void enabledMailWithoutSmtpHostFailsFast() {
        assertThatThrownBy(() -> configuration.emailSender(properties(true), provider(null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AURELLE_MAIL_HOST");
    }

    private static MailProperties properties(boolean enabled) {
        return new MailProperties(enabled, "no-reply@example.test", "Aurelle", null);
    }

    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (sender != null) {
            factory.addBean("mailSender", sender);
        }
        return factory.getBeanProvider(JavaMailSender.class);
    }
}
