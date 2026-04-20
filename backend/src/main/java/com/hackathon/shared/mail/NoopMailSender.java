package com.hackathon.shared.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

/**
 * Test-profile stub for JavaMailSender. Boots without needing a running SMTP server
 * so tests don't require SMTP plumbing. @Primary makes @MockitoSpyBean JavaMailSender
 * resolve to this instance instead of Spring's auto-configured JavaMailSenderImpl.
 * Same pattern as NoopTokenRevocationService and NoopPresencePublisher.
 */
@Component
@Profile("test")
@Primary
public class NoopMailSender implements JavaMailSender {

  // Real Session, not null — MimeMessageHelper dereferences getSession() on some paths
  // (charset resolution, header encoding) and would NPE on a null Session.
  private static final Session SESSION = Session.getInstance(new Properties());

  @Override
  public MimeMessage createMimeMessage() {
    return new MimeMessage(SESSION);
  }

  @Override
  public MimeMessage createMimeMessage(InputStream contentStream) {
    try {
      return new MimeMessage(SESSION, contentStream);
    } catch (Exception e) {
      throw new MailParseException("Failed to parse MIME stream", toIOException(e));
    }
  }

  private static IOException toIOException(Exception e) {
    return e instanceof IOException io ? io : new IOException(e);
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
