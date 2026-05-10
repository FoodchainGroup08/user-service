package com.microservices.user.service.impl;

import com.microservices.user.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Used when Kafka is not configured (e.g. local tests); {@link KafkaEmailNotificationService} replaces this in production.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(KafkaEmailNotificationService.class)
public class NoOpEmailService implements EmailService {

    @Override
    public void sendWelcome(String toEmail, String toName) {
        log.debug("Email not configured: skip welcome to {}", toEmail);
    }

    @Override
    public void sendPasswordReset(String toEmail, String toName, String resetLink) {
        log.debug("Email not configured: skip password reset to {}", toEmail);
    }
}
