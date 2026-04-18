package com.hackathon.features.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.TestSecurityConfig;
import com.hackathon.shared.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class UserControllerTest {
  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private UserService userService;

  @MockBean private JwtTokenProvider jwtTokenProvider;

  private User testUser;
  private UUID testUserId;

  @BeforeEach
  void setUp() {
    testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    testUser = new User();
    testUser.setId(testUserId);
    testUser.setEmail("test@example.com");
    testUser.setUsername("testuser");
  }

  @Test
  void testRegister_Success() throws Exception {
    UserController.RegisterRequest request = new UserController.RegisterRequest();
    request.setEmail("newuser@example.com");
    request.setUsername("newuser");
    request.setPassword("password123");

    when(userService.registerUser("newuser@example.com", "newuser", "password123"))
        .thenReturn(testUser);

    mockMvc
        .perform(
            post("/api/users/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.username").value("testuser"));
  }

  @Test
  void testRegister_EmailAlreadyExists() throws Exception {
    UserController.RegisterRequest request = new UserController.RegisterRequest();
    request.setEmail("existing@example.com");
    request.setUsername("newuser");
    request.setPassword("password123");

    when(userService.registerUser("existing@example.com", "newuser", "password123"))
        .thenThrow(new IllegalArgumentException("Email already exists"));

    mockMvc
        .perform(
            post("/api/users/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testLogin_Success() throws Exception {
    UserController.LoginRequest request = new UserController.LoginRequest();
    request.setEmail("test@example.com");
    request.setPassword("password123");

    when(userService.authenticateUser("test@example.com", "password123"))
        .thenReturn(testUser);
    when(jwtTokenProvider.generateToken(testUserId, "testuser")).thenReturn("valid-jwt-token");

    mockMvc
        .perform(
            post("/api/users/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("valid-jwt-token"))
        .andExpect(jsonPath("$.user.id").value(testUserId.toString()))
        .andExpect(jsonPath("$.user.email").value("test@example.com"));
  }

  @Test
  void testLogin_InvalidCredentials() throws Exception {
    UserController.LoginRequest request = new UserController.LoginRequest();
    request.setEmail("test@example.com");
    request.setPassword("wrongpassword");

    when(userService.authenticateUser("test@example.com", "wrongpassword"))
        .thenThrow(new IllegalArgumentException("Invalid credentials"));

    mockMvc
        .perform(
            post("/api/users/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void testGetCurrentUser_Success() throws Exception {
    when(jwtTokenProvider.validateToken("valid-jwt-token")).thenReturn(true);
    when(jwtTokenProvider.getUserIdFromToken("valid-jwt-token")).thenReturn(testUserId);
    when(userService.getUserById(testUserId)).thenReturn(testUser);

    mockMvc
        .perform(
            get("/api/users/me")
                .with(csrf())
                .header("Authorization", "Bearer valid-jwt-token")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(testUserId.toString()))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.username").value("testuser"));
  }

  @Test
  void testLogout_Success() throws Exception {
    mockMvc
        .perform(post("/api/users/logout")
                .with(csrf())
                .header("Authorization", "Bearer valid-jwt-token"))
        .andExpect(status().isOk());
  }
}
