package com.microservices.user.service;

public interface EmailService {
    void sendWelcome(String toEmail, String toName);
    void sendPasswordReset(String toEmail, String toName, String resetLink);
}
