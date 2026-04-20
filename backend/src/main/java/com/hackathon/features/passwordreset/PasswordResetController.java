package com.hackathon.features.passwordreset;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

  private final PasswordResetService passwordResetService;

  record RequestBodyDto(String email) {}

  record ConfirmBodyDto(String token, String newPassword) {}

  @PostMapping("/request")
  public ResponseEntity<Void> request(@RequestBody RequestBodyDto body) {
    if (body == null || body.email() == null || body.email().isBlank()) {
      // Still 204 — enumeration protection applies to bad input too.
      return ResponseEntity.noContent().build();
    }
    passwordResetService.requestReset(body.email());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/confirm")
  public ResponseEntity<Void> confirm(@RequestBody ConfirmBodyDto body) {
    try {
      passwordResetService.confirmReset(body.token(), body.newPassword());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.noContent().build();
  }
}
