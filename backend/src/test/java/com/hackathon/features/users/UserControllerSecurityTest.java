package com.hackathon.features.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests that unauthenticated requests to the new account-management endpoints are rejected with
 * 401. These tests intentionally do NOT import TestSecurityConfig so that the real SecurityConfig
 * (JWT filter + authenticated-only policy) is active.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void changePassword_withoutJwt_rejects() throws Exception {
    mockMvc
        .perform(
            patch("/api/users/me/password")
                .contentType("application/json")
                .content("{\"oldPassword\":\"x\",\"newPassword\":\"yyyyyyyy\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteAccount_withoutJwt_rejects() throws Exception {
    mockMvc.perform(delete("/api/users/me")).andExpect(status().isUnauthorized());
  }
}
