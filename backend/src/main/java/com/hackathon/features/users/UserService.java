package com.hackathon.features.users;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public User registerUser(String email, String username, String password) {
    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email already exists");
    }
    if (userRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("Username already exists");
    }

    User user = new User();
    user.setEmail(email);
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(password));

    return userRepository.save(user);
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

  public User getUserById(Integer userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }
}
