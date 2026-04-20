package com.hackathon.features.users;

import com.hackathon.features.jabber.JabberProvisioningService;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  /** ObjectProvider so the Jabber feature stays optional — tests / profiles without
   *  the bridge bean don't fail user registration. */
  private final ObjectProvider<JabberProvisioningService> jabberProvisioning;

  public User registerUser(String email, String username, String password) {
    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email already exists");
    }
    if (userRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("Username already exists");
    }

    User user =
        User.builder()
            .email(email)
            .username(username)
            .passwordHash(passwordEncoder.encode(password))
            .passwordChangedAt(OffsetDateTime.now())
            .build();

    User saved = userRepository.save(user);
    jabberProvisioning.ifAvailable(svc -> svc.provisionFor(saved));
    return saved;
  }

  public User authenticateUser(String email, String password) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new IllegalArgumentException("Invalid password");
    }

    return user;
  }

  public User getUserById(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  public User getUserByUsername(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  private static final int MIN_PASSWORD_LENGTH = 8;

  public static class WrongPasswordException extends RuntimeException {
    public WrongPasswordException() { super("Old password is incorrect"); }
  }

  public void changePassword(UUID userId, String oldPassword, String newPassword) {
    User user = userRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
      throw new WrongPasswordException();
    }
    if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "New password must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setPasswordChangedAt(OffsetDateTime.now());
    userRepository.save(user);
  }

  public void deleteAccount(UUID userId) {
    User user = userRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    jabberProvisioning.ifAvailable(svc -> svc.deprovisionFor(user));
    userRepository.deleteById(userId);
  }
}
