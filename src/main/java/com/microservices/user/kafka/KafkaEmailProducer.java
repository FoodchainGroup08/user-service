package com.microservices.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEmailProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.email-send:notification.email.send}")
    private String emailTopic;

    public void sendEmail(String toEmail, String toName, String subject,
                          String htmlContent, String emailType) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("toEmail",     toEmail);
            payload.put("toName",      toName);
            payload.put("subject",     subject);
            payload.put("htmlContent", htmlContent);
            payload.put("emailType",   emailType);

            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(emailTopic, toEmail, json);
            log.info("Published email event type={} to={}", emailType, toEmail);
        } catch (Exception e) {
            log.error("Failed to publish email event type={} to={}: {}", emailType, toEmail, e.getMessage());
        }
    }
}
