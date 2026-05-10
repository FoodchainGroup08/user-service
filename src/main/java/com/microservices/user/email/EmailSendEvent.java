package com.microservices.user.email;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published to Kafka topic {@code notification.email.send}; consumed by notifications-service (Brevo).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailSendEvent {
    private String toEmail;
    private String toName;
    private String subject;
    private String htmlContent;
    /** e.g. WELCOME, PASSWORD_RESET */
    private String emailType;
}
