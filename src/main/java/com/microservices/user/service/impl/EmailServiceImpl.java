package com.microservices.user.service.impl;

import com.microservices.user.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Override
    @Async
    public void sendVerificationEmail(String to, String name, String verifyLink) {
        String subject = "FoodChain — Verify your email address";
        String html = """
                <!DOCTYPE html>
                <html>
                <body style="font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:0;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:30px 0;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;">
                        <tr><td style="background:#e85d04;padding:28px 32px;">
                          <h1 style="color:#ffffff;margin:0;font-size:22px;">FoodChain</h1>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <h2 style="color:#222;margin-top:0;">Welcome, %s!</h2>
                          <p style="color:#555;line-height:1.6;">
                            Thank you for creating a FoodChain account.<br>
                            Please verify your email address by clicking the button below.
                            This link expires in <strong>24 hours</strong>.
                          </p>
                          <div style="text-align:center;margin:32px 0;">
                            <a href="%s"
                               style="background:#e85d04;color:#ffffff;text-decoration:none;
                                      padding:14px 32px;border-radius:6px;font-size:16px;
                                      font-weight:bold;display:inline-block;">
                              Verify Email Address
                            </a>
                          </div>
                          <p style="color:#999;font-size:13px;">
                            If you didn't create this account, you can safely ignore this email.
                          </p>
                          <hr style="border:none;border-top:1px solid #eee;margin:24px 0;">
                          <p style="color:#bbb;font-size:12px;text-align:center;">
                            FoodChain &mdash; Group 08 Capstone Project
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(name, verifyLink);

        send(to, subject, html);
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        String subject = "FoodChain — Reset your password";
        String html = """
                <!DOCTYPE html>
                <html>
                <body style="font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:0;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:30px 0;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;">
                        <tr><td style="background:#e85d04;padding:28px 32px;">
                          <h1 style="color:#ffffff;margin:0;font-size:22px;">FoodChain</h1>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <h2 style="color:#222;margin-top:0;">Password Reset Request</h2>
                          <p style="color:#555;line-height:1.6;">
                            Hi %s, we received a request to reset the password for your FoodChain account.<br>
                            Click the button below to choose a new password.
                            This link expires in <strong>1 hour</strong>.
                          </p>
                          <div style="text-align:center;margin:32px 0;">
                            <a href="%s"
                               style="background:#e85d04;color:#ffffff;text-decoration:none;
                                      padding:14px 32px;border-radius:6px;font-size:16px;
                                      font-weight:bold;display:inline-block;">
                              Reset Password
                            </a>
                          </div>
                          <p style="color:#999;font-size:13px;">
                            If you didn't request a password reset, you can safely ignore this email.
                            Your password will not be changed.
                          </p>
                          <hr style="border:none;border-top:1px solid #eee;margin:24px 0;">
                          <p style="color:#bbb;font-size:12px;text-align:center;">
                            FoodChain &mdash; Group 08 Capstone Project
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(name, resetLink);

        send(to, subject, html);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
