package com.hackathon.shared.mail;

import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

/**
 * Test-profile stub for JavaMailSender. Boots without needing a running SMTP server
 * and lets test code verify "send was called" by spying this bean. Same pattern as
 * NoopTokenRevocationService and NoopPresencePublisher.
 */
@Component
@Profile("test")
@Primary
public class NoopMailSender implements JavaMailSender {

  @Override
  public MimeMessage createMimeMessage() {
    return new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null);
  }

  @Override
  public MimeMessage createMimeMessage(InputStream contentStream) {
    return createMimeMessage();
  }

  @Override
  public void send(MimeMessage mimeMessage) {}

  @Override
  public void send(MimeMessage... mimeMessages) {}

  @Override
  public void send(MimeMessagePreparator mimeMessagePreparator) {}

  @Override
  public void send(MimeMessagePreparator... mimeMessagePreparators) {}

  @Override
  public void send(SimpleMailMessage simpleMessage) {}

  @Override
  public void send(SimpleMailMessage... simpleMessages) {}
}
