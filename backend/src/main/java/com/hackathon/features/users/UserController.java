package com.hackathon.features.users;

import com.hackathon.shared.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserService userService;
  private final JwtTokenProvider jwtTokenProvider;

  public UserController(UserService userService, JwtTokenProvider jwtTokenProvider) {
    this.userService = userService;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
    try {
      User user = userService.registerUser(request.getEmail(), request.getUsername(),
          request.getPassword());
      return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(user));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
    try {
      User user = userService.authenticateUser(request.getEmail(), request.getPassword());
      String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
      return ResponseEntity.ok(new LoginResponse(token, new UserResponse(user)));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  @GetMapping("/me")
  public ResponseEntity<UserResponse> getCurrentUser(
      @RequestHeader("Authorization") String authHeader) {
    try {
      String token = authHeader.replace("Bearer ", "");
      if (!jwtTokenProvider.validateToken(token)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      Integer userId = jwtTokenProvider.getUserIdFromToken(token);
      User user = userService.getUserById(userId);
      return ResponseEntity.ok(new UserResponse(user));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
    return ResponseEntity.ok().build();
  }

  public static class RegisterRequest {
    private String email;
    private String username;
    private String password;

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }

  public static class LoginRequest {
    private String email;
    private String password;

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }

  public static class UserResponse {
    private Integer id;
    private String email;
    private String username;

    public UserResponse(User user) {
      this.id = user.getId();
      this.email = user.getEmail();
      this.username = user.getUsername();
    }

    public Integer getId() {
      return id;
    }

    public String getEmail() {
      return email;
    }

    public String getUsername() {
      return username;
    }
  }

  public static class LoginResponse {
    private String token;
    private UserResponse user;

    public LoginResponse(String token, UserResponse user) {
      this.token = token;
      this.user = user;
    }

    public String getToken() {
      return token;
    }

    public UserResponse getUser() {
      return user;
    }
  }
}
