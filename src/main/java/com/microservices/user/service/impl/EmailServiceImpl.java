package com.microservices.user.service.impl;

import com.microservices.user.kafka.KafkaEmailProducer;
import com.microservices.user.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Publishes transactional email events to the {@code notification.email.send} Kafka topic.
 * The notifications-service consumes that topic and delivers via Brevo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final KafkaEmailProducer kafkaEmailProducer;

    @Value("${app.mail.from:no-reply@foodchain.local}")
    private String fromAddress;

    // ── Verification ──────────────────────────────────────────────────────────

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
                      <table width="560" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;overflow:hidden;">
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
                            FoodChain &mdash; Multi-Branch Restaurant Platform
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escapeHtml(name), verifyLink);

        kafkaEmailProducer.sendEmail(to, name, subject, html, "EMAIL_VERIFICATION");
        log.info("Queued verification email for {}", to);
    }

    // ── Password reset ────────────────────────────────────────────────────────

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
                      <table width="560" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;overflow:hidden;">
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
                            FoodChain &mdash; Multi-Branch Restaurant Platform
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escapeHtml(name), resetLink);

        kafkaEmailProducer.sendEmail(to, name, subject, html, "PASSWORD_RESET");
        log.info("Queued password-reset email for {}", to);
    }

    // ── Sign-in notification ──────────────────────────────────────────────────

    @Override
    @Async
    public void sendSignInEmail(String to, String name) {
        String subject = "FoodChain — New sign-in to your account";
        String html = """
                <!DOCTYPE html>
                <html>
                <body style="font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:0;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:30px 0;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;overflow:hidden;">
                        <tr><td style="background:#e85d04;padding:28px 32px;">
                          <h1 style="color:#ffffff;margin:0;font-size:22px;">FoodChain</h1>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <h2 style="color:#222;margin-top:0;">Welcome back, %s!</h2>
                          <p style="color:#555;line-height:1.6;">
                            We noticed a new sign-in to your FoodChain account.
                            If this was you, no action is needed.
                          </p>
                          <p style="color:#555;line-height:1.6;">
                            If you did not sign in, please
                            <a href="#" style="color:#e85d04;">reset your password</a> immediately.
                          </p>
                          <hr style="border:none;border-top:1px solid #eee;margin:24px 0;">
                          <p style="color:#bbb;font-size:12px;text-align:center;">
                            FoodChain &mdash; Multi-Branch Restaurant Platform
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escapeHtml(name));

        kafkaEmailProducer.sendEmail(to, name, subject, html, "SIGN_IN");
        log.info("Queued sign-in notification email for {}", to);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
