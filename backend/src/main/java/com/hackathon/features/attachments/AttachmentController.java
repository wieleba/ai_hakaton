package com.hackathon.features.attachments;

import com.hackathon.features.users.UserService;
import com.hackathon.shared.storage.StorageService;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {
  private final AttachmentService attachmentService;
  private final StorageService storageService;
  private final UserService userService;

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping("/{id}/content")
  public ResponseEntity<InputStreamResource> getContent(
      @PathVariable UUID id, Authentication authentication) {
    Optional<AttachmentLookupResult> hit = attachmentService.lookup(id);
    if (hit.isEmpty()) return ResponseEntity.notFound().build();
    if (!attachmentService.isAuthorized(hit.get(), currentUserId(authentication))) {
      return ResponseEntity.status(403).build();
    }
    AttachmentLookupResult a = hit.get();
    InputStream content = storageService.load(a.storageKey());

    String disposition = AttachmentPolicy.isImage(a.mimeType()) ? "inline" : "attachment";
    String encoded = URLEncoder.encode(a.filename(), StandardCharsets.UTF_8).replace("+", "%20");
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.CONTENT_DISPOSITION,
        disposition + "; filename*=UTF-8''" + encoded);
    headers.setContentLength(a.sizeBytes());

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(MediaType.parseMediaType(a.mimeType()))
        .body(new InputStreamResource(content));
  }
}
