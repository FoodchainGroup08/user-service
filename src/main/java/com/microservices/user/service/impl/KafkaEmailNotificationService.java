package com.microservices.user.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.user.email.EmailSendEvent;
import com.microservices.user.email.EmailTemplates;
import com.microservices.user.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaEmailNotificationService implements EmailService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic-email-send:notification.email.send}")
    private String emailTopic;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void sendWelcome(String toEmail, String toName) {
        publish(EmailSendEvent.builder()
                .toEmail(toEmail)
                .toName(toName)
                .subject("Welcome to FoodChain")
                .htmlContent(EmailTemplates.welcome(toName, frontendUrl))
                .emailType("WELCOME")
                .build());
    }

    @Override
    public void sendPasswordReset(String toEmail, String toName, String resetLink) {
        publish(EmailSendEvent.builder()
                .toEmail(toEmail)
                .toName(toName)
                .subject("Reset your FoodChain password")
                .htmlContent(EmailTemplates.passwordReset(toName, resetLink))
                .emailType("PASSWORD_RESET")
                .build());
    }

    @Override
    public void sendEmailVerification(String toEmail, String toName, String verifyLink) {
        publish(EmailSendEvent.builder()
                .toEmail(toEmail)
                .toName(toName)
                .subject("Verify your FoodChain account")
                .htmlContent(EmailTemplates.emailVerification(toName, verifyLink))
                .emailType("EMAIL_VERIFICATION")
                .build());
    }

    private void publish(EmailSendEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(emailTopic, event.getToEmail(), json)
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish email event type={} to={}: {}",
                                    event.getEmailType(), event.getToEmail(), ex.getMessage());
                        } else {
                            log.debug("Published email event type={} to Kafka partition={} offset={}",
                                    event.getEmailType(),
                                    r != null && r.getRecordMetadata() != null
                                            ? r.getRecordMetadata().partition() : "?",
                                    r != null && r.getRecordMetadata() != null
                                            ? r.getRecordMetadata().offset() : "?");
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Could not serialize email event: {}", e.getMessage());
        }
    }
}
