package com.hackathon.features.passwordreset;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetEmailBuilder {

  private final JavaMailSender mailSender;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Value("${app.mail-from:noreply@chat.local}")
  private String mailFrom;

  public MimeMessage build(String toEmail, String rawToken) throws MessagingException {
    String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
    String plain =
        "You (or someone else) requested a password reset for your account.\n\n"
            + "To set a new password, open this link within 30 minutes:\n"
            + "  "
            + link
            + "\n\n"
            + "If you didn't request this, you can ignore this email"
            + " — your password stays unchanged.\n";
    String html =
        "<p>You (or someone else) requested a password reset for your account.</p>"
            + "<p>To set a new password, open this link within 30 minutes:</p>"
            + "<p><a href=\""
            + link
            + "\">"
            + link
            + "</a></p>"
            + "<p>If you didn't request this, you can ignore this email"
            + " — your password stays unchanged.</p>";

    MimeMessage msg = mailSender.createMimeMessage();
    MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
    h.setFrom(mailFrom);
    h.setTo(toEmail);
    h.setSubject("Reset your password");
    h.setText(plain, html);
    return msg;
  }
}
