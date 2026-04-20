package com.hackathon.shared.exception;

import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Without this, unannotated service-level exceptions (IllegalArgumentException,
 * NoSuchElementException) bubbled up to Spring's default resolver chain and
 * produced opaque 500s — or worse, the authentication entry point caught them
 * and returned a misleading 401 (seen on /api/rooms/{id}/invitations when the
 * invitee username didn't exist). Map them to 400/404 with a JSON body so the
 * frontend can actually surface the message.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  public record ErrorResponse(String message) {}

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(safeMessage(e)));
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(safeMessage(e)));
  }

  private static String safeMessage(Exception e) {
    String m = e.getMessage();
    return m == null || m.isBlank() ? "Bad request" : m;
  }
}
