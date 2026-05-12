package com.microservices.user.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BrevoEmailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient = RestClient.create();

    @Value("${app.brevo.api-key:}")
    private String apiKey;

    @Value("${app.brevo.sender-email:}")
    private String senderEmail;

    @Value("${app.brevo.sender-name:FoodChain}")
    private String senderName;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && senderEmail != null && !senderEmail.isBlank();
    }

    public void send(String toEmail, String toName, String subject, String htmlContent) {
        if (!isConfigured()) {
            log.warn("Brevo not configured — skipping fallback send to {}", toEmail);
            return;
        }

        String displayName = (toName != null && !toName.isBlank()) ? toName : toEmail;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", Map.of("name", senderName, "email", senderEmail));
        body.put("to", List.of(Map.of("email", toEmail, "name", displayName)));
        body.put("subject", subject);
        body.put("htmlContent", htmlContent);

        restClient.post()
                .uri(BREVO_API_URL)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Brevo fallback email sent to {}: {}", toEmail, subject);
    }
}
