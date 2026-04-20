package com.hackathon.features.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.features.sessions.SessionDisconnector;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import com.hackathon.shared.security.TokenHashing;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordResetTokenRepository tokenRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private SessionDisconnector sessionDisconnector;
  @MockitoBean private SimpMessagingTemplate messagingTemplate;

  @Test
  void request_returns204_forAnyEmail() throws Exception {
    mvc.perform(post("/api/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody@x.test\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void request_returns204_forBlankEmail() throws Exception {
    mvc.perform(post("/api/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void confirm_unknownToken_is400() throws Exception {
    mvc.perform(post("/api/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"nope\",\"newPassword\":\"validpass1\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void confirm_shortPassword_is400() throws Exception {
    mvc.perform(post("/api/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"any\",\"newPassword\":\"x\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void confirm_validToken_is204_andNewLoginWorks() throws Exception {
    User u = new User();
    u.setUsername("ctrl-" + System.nanoTime());
    u.setEmail(u.getUsername() + "@x.test");
    u.setPasswordHash(passwordEncoder.encode("oldpass123"));
    userRepository.save(u);

    String raw = "raw-token-" + System.nanoTime();
    PasswordResetToken t =
        PasswordResetToken.builder()
            .tokenHash(TokenHashing.sha256Hex(raw))
            .userId(u.getId())
            .expiresAt(OffsetDateTime.now().plusMinutes(5))
            .build();
    tokenRepository.save(t);

    mvc.perform(post("/api/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + raw + "\",\"newPassword\":\"newpass123\"}"))
        .andExpect(status().isNoContent());

    // Login with new password succeeds.
    MvcResult login =
        mvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"newpass123\"}"))
            .andReturn();
    assertThat(login.getResponse().getStatus()).isEqualTo(200);
  }
}
