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
    private final BrevoEmailService brevoEmailService;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Override
    @Async
    public void sendVerificationEmail(String to, String name, String verifyLink) {
        String subject = "FoodChain — Verify your email address";
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1.0">
                  <title>Verify your FoodChain account</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f3f4f6;font-family:Arial,Helvetica,sans-serif;-webkit-text-size-adjust:100%%;">
                  <table width="100%%" cellpadding="0" cellspacing="0" border="0"
                         style="background-color:#f3f4f6;padding:40px 16px;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0" border="0"
                             style="max-width:600px;width:100%%;border-radius:12px;overflow:hidden;
                                    box-shadow:0 4px 24px rgba(0,0,0,0.10);">

                        <!-- Brand header -->
                        <tr>
                          <td style="background-color:#e85d04;padding:22px 32px;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                              <tr>
                                <td style="vertical-align:middle;">
                                  <span style="font-size:22px;font-weight:800;color:#ffffff;letter-spacing:-0.5px;">Food</span><span style="font-size:22px;font-weight:800;color:#ffd8b4;">Chain</span>
                                </td>
                                <td style="text-align:right;vertical-align:middle;">
                                  <span style="font-size:10px;color:#ffa87d;text-transform:uppercase;letter-spacing:1.5px;">Account Verification</span>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- Status banner -->
                        <tr>
                          <td style="background-color:#2563eb;padding:18px 32px;">
                            <p style="margin:0;font-size:19px;font-weight:700;color:#ffffff;line-height:1.25;">Verify your email address</p>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="background-color:#ffffff;padding:32px 32px 24px;">
                            <p style="margin:0 0 6px;font-size:16px;color:#111827;">Hi <strong>%s</strong>,</p>
                            <p style="margin:0 0 24px;font-size:15px;color:#4b5563;line-height:1.7;">
                              Welcome to FoodChain! We're excited to have you on board.<br>
                              Please verify your email address by clicking the button below.
                              This link expires in <strong>24 hours</strong>.
                            </p>

                            <!-- CTA button -->
                            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
                              <tr>
                                <td align="center">
                                  <a href="%s"
                                     style="display:inline-block;background-color:#e85d04;color:#ffffff;
                                            text-decoration:none;font-size:15px;font-weight:700;
                                            padding:14px 36px;border-radius:8px;letter-spacing:0.2px;">
                                    Verify Email Address
                                  </a>
                                </td>
                              </tr>
                            </table>

                            <!-- Security notice -->
                            <table width="100%%" cellpadding="0" cellspacing="0"
                                   style="background-color:#f0f9ff;border-radius:6px;margin-bottom:4px;">
                              <tr>
                                <td style="padding:12px 16px;border-left:3px solid #2563eb;border-radius:0 6px 6px 0;">
                                  <p style="margin:0;font-size:13px;color:#1e40af;line-height:1.5;">
                                    If you didn't create a FoodChain account, you can safely ignore this email. No action is needed.
                                  </p>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background-color:#f9fafb;padding:16px 32px;text-align:center;border-top:1px solid #e5e7eb;">
                            <p style="margin:0 0 3px;font-size:11px;color:#9ca3af;">This email was sent to you by FoodChain as part of account registration.</p>
                            <p style="margin:0;font-size:10px;color:#d1d5db;">&copy; 2025 FoodChain &mdash; All rights reserved.</p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(esc(name), verifyLink);

        send(to, subject, html);
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        String subject = "FoodChain — Reset your password";
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1.0">
                  <title>FoodChain password reset</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f3f4f6;font-family:Arial,Helvetica,sans-serif;-webkit-text-size-adjust:100%%;">
                  <table width="100%%" cellpadding="0" cellspacing="0" border="0"
                         style="background-color:#f3f4f6;padding:40px 16px;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0" border="0"
                             style="max-width:600px;width:100%%;border-radius:12px;overflow:hidden;
                                    box-shadow:0 4px 24px rgba(0,0,0,0.10);">

                        <!-- Brand header -->
                        <tr>
                          <td style="background-color:#e85d04;padding:22px 32px;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                              <tr>
                                <td style="vertical-align:middle;">
                                  <span style="font-size:22px;font-weight:800;color:#ffffff;letter-spacing:-0.5px;">Food</span><span style="font-size:22px;font-weight:800;color:#ffd8b4;">Chain</span>
                                </td>
                                <td style="text-align:right;vertical-align:middle;">
                                  <span style="font-size:10px;color:#ffa87d;text-transform:uppercase;letter-spacing:1.5px;">Account Security</span>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- Status banner -->
                        <tr>
                          <td style="background-color:#d97706;padding:18px 32px;">
                            <p style="margin:0;font-size:19px;font-weight:700;color:#ffffff;line-height:1.25;">Password reset request</p>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="background-color:#ffffff;padding:32px 32px 24px;">
                            <p style="margin:0 0 6px;font-size:16px;color:#111827;">Hi <strong>%s</strong>,</p>
                            <p style="margin:0 0 24px;font-size:15px;color:#4b5563;line-height:1.7;">
                              We received a request to reset the password for your FoodChain account.<br>
                              Click the button below to choose a new password.
                              This link expires in <strong>1 hour</strong>.
                            </p>

                            <!-- CTA button -->
                            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
                              <tr>
                                <td align="center">
                                  <a href="%s"
                                     style="display:inline-block;background-color:#e85d04;color:#ffffff;
                                            text-decoration:none;font-size:15px;font-weight:700;
                                            padding:14px 36px;border-radius:8px;letter-spacing:0.2px;">
                                    Reset My Password
                                  </a>
                                </td>
                              </tr>
                            </table>

                            <!-- Security notice -->
                            <table width="100%%" cellpadding="0" cellspacing="0"
                                   style="background-color:#fffbeb;border-radius:6px;margin-bottom:4px;">
                              <tr>
                                <td style="padding:12px 16px;border-left:3px solid #f59e0b;border-radius:0 6px 6px 0;">
                                  <p style="margin:0;font-size:13px;color:#92400e;line-height:1.5;">
                                    If you didn't request a password reset, please ignore this email. Your password will remain unchanged.
                                  </p>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background-color:#f9fafb;padding:16px 32px;text-align:center;border-top:1px solid #e5e7eb;">
                            <p style="margin:0 0 3px;font-size:11px;color:#9ca3af;">This email was sent to you because a password reset was requested for your account.</p>
                            <p style="margin:0;font-size:10px;color:#d1d5db;">&copy; 2025 FoodChain &mdash; All rights reserved.</p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(esc(name), resetLink);

        send(to, subject, html);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
            log.info("Email sent via Gmail to {}: {}", to, subject);
        } catch (MessagingException | MailException e) {
            log.warn("Gmail SMTP failed for {} ({}), trying Brevo fallback...", to, e.getMessage());
            try {
                brevoEmailService.send(to, null, subject, html);
            } catch (Exception brevoEx) {
                log.error("Brevo fallback also failed for {}: {}", to, brevoEx.getMessage());
            }
        }
    }
}
