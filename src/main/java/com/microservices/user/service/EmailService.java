package com.microservices.user.service;

public interface EmailService {
    void sendVerificationEmail(String to, String name, String verifyLink);
    void sendPasswordResetEmail(String to, String name, String resetLink);
}
