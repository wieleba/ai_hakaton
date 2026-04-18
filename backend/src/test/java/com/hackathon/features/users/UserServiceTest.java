package com.hackathon.features.users;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private UserService userService;

  private User testUser;

  @BeforeEach
  void setUp() {
    testUser = new User();
    testUser.setId(1);
    testUser.setEmail("test@example.com");
    testUser.setUsername("testuser");
    testUser.setPasswordHash("hashedpassword");
  }

  @Test
  void testRegisterUser_Success() {
    String email = "newuser@example.com";
    String username = "newuser";
    String password = "password123";
    String hashedPassword = "hashedpassword";

    User savedUser = new User();
    savedUser.setId(1);
    savedUser.setEmail(email);
    savedUser.setUsername(username);
    savedUser.setPasswordHash(hashedPassword);

    when(userRepository.existsByEmail(email)).thenReturn(false);
    when(userRepository.existsByUsername(username)).thenReturn(false);
    when(passwordEncoder.encode(password)).thenReturn(hashedPassword);
    when(userRepository.save(any(User.class))).thenReturn(savedUser);

    User result = userService.registerUser(email, username, password);

    assertNotNull(result);
    assertEquals(email, result.getEmail());
    assertEquals(username, result.getUsername());
    verify(userRepository).save(any(User.class));
  }

  @Test
  void testRegisterUser_EmailAlreadyExists() {
    String email = "existing@example.com";
    String username = "newuser";
    String password = "password123";

    when(userRepository.existsByEmail(email)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class,
        () -> userService.registerUser(email, username, password));
  }

  @Test
  void testRegisterUser_UsernameAlreadyExists() {
    String email = "newuser@example.com";
    String username = "existinguser";
    String password = "password123";

    when(userRepository.existsByEmail(email)).thenReturn(false);
    when(userRepository.existsByUsername(username)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class,
        () -> userService.registerUser(email, username, password));
  }

  @Test
  void testAuthenticateUser_Success() {
    String email = "test@example.com";
    String password = "password123";
    String hashedPassword = "hashedpassword";

    testUser.setPasswordHash(hashedPassword);
    when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches(password, hashedPassword)).thenReturn(true);

    User result = userService.authenticateUser(email, password);

    assertNotNull(result);
    assertEquals(email, result.getEmail());
  }

  @Test
  void testAuthenticateUser_WrongPassword() {
    String email = "test@example.com";
    String password = "wrongpassword";
    String hashedPassword = "hashedpassword";

    testUser.setPasswordHash(hashedPassword);
    when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches(password, hashedPassword)).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class, () -> userService.authenticateUser(email, password));
  }

  @Test
  void testAuthenticateUser_UserNotFound() {
    String email = "notfound@example.com";
    String password = "password123";

    when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class, () -> userService.authenticateUser(email, password));
  }

  @Test
  void testGetUserById_Success() {
    when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

    User result = userService.getUserById(1);

    assertNotNull(result);
    assertEquals("testuser", result.getUsername());
  }

  @Test
  void testGetUserById_NotFound() {
    when(userRepository.findById(999)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> userService.getUserById(999));
  }
}
