package com.hackathon.features.users;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hackathon.features.jabber.JabberProvisioningService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private ObjectProvider<JabberProvisioningService> jabberProvisioning;

  @InjectMocks private UserService userService;

  private User testUser;
  private UUID testUserId;

  @BeforeEach
  void setUp() {
    testUserId = UUID.randomUUID();
    testUser = new User();
    testUser.setId(testUserId);
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

    UUID savedUserId = UUID.randomUUID();
    User savedUser = new User();
    savedUser.setId(savedUserId);
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
    when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

    User result = userService.getUserById(testUserId);

    assertNotNull(result);
    assertEquals("testuser", result.getUsername());
  }

  @Test
  void testGetUserById_NotFound() {
    UUID unknownId = UUID.randomUUID();
    when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> userService.getUserById(unknownId));
  }

  @Test
  void changePassword_happyPath_updatesHash() {
    testUser.setPasswordHash("hashedpassword");
    when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("password12345", "hashedpassword")).thenReturn(true);
    when(passwordEncoder.encode("newpassword123")).thenReturn("newhashedpassword");
    when(userRepository.save(any(User.class))).thenReturn(testUser);

    userService.changePassword(testUserId, "password12345", "newpassword123");

    verify(userRepository).save(testUser);
    assertEquals("newhashedpassword", testUser.getPasswordHash());
  }

  @Test
  void changePassword_wrongOldPassword_throws() {
    testUser.setPasswordHash("hashedpassword");
    when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("wrong-old", "hashedpassword")).thenReturn(false);

    assertThrows(UserService.WrongPasswordException.class,
        () -> userService.changePassword(testUserId, "wrong-old", "newpassword123"));
  }

  @Test
  void changePassword_tooShortNewPassword_throws() {
    testUser.setPasswordHash("hashedpassword");
    when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("password12345", "hashedpassword")).thenReturn(true);

    assertThrows(IllegalArgumentException.class,
        () -> userService.changePassword(testUserId, "password12345", "short"));
  }

  @Test
  void deleteAccount_removesUserRow() {
    when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

    userService.deleteAccount(testUserId);

    verify(userRepository).deleteById(testUserId);
  }

  @Test
  void deleteAccount_missingUser_throws() {
    UUID missingId = UUID.randomUUID();
    when(userRepository.findById(missingId)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class,
        () -> userService.deleteAccount(missingId));
  }
}
