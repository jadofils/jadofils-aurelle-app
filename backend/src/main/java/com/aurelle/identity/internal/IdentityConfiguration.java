package com.aurelle.identity.internal;

import com.aurelle.identity.SessionAuthenticationFilter;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Wiring for the identity module: tokens, limits, the session filter and the bounded mail executor. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfiguration {

    static final String MAIL_EXECUTOR = "mailExecutor";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureTokens secureTokens(IdentityProperties properties) {
        return new SecureTokens(properties.codeHmacKey(), new SecureRandom());
    }

    /** 10 sign-in attempts per identifier per 15 minutes (on top of the per-account lock). */
    @Bean
    RateLimiter signInLimiter(Clock clock) {
        return new RateLimiter(10, Duration.ofMinutes(15), clock);
    }

    /**
     * 100 sign-in attempts per client address per 15 minutes: a whole shop or office often shares one
     * public IP, so this only stops bulk guessing across many accounts.
     */
    @Bean
    RateLimiter clientLimiter(Clock clock) {
        return new RateLimiter(100, Duration.ofMinutes(15), clock);
    }

    /** Sign-up, resets, resends, password changes: 5 per key per hour. */
    @Bean
    RateLimiter sensitiveLimiter(Clock clock) {
        return new RateLimiter(5, Duration.ofHours(1), clock);
    }

    @Bean
    SessionAuthenticationFilter sessionAuthenticationFilter(AuthService authService, IdentityProperties properties) {
        return new SessionAuthenticationFilter(authService, properties.allowedOrigins());
    }

    /** Registered inside Spring Security's chain only (see SecurityConfiguration), not as a servlet filter. */
    @Bean
    FilterRegistrationBean<SessionAuthenticationFilter> sessionFilterRegistration(SessionAuthenticationFilter filter) {
        FilterRegistrationBean<SessionAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** Bounded: 2–4 threads, 500 queued e-mails; beyond that new e-mails are dropped and logged. */
    @Bean(MAIL_EXECUTOR)
    ThreadPoolTaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
