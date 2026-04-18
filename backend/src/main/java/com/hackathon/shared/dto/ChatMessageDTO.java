package com.hackathon.shared.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {
  private UUID id;
  private UUID roomId;
  private UUID userId;
  private String username;
  private String text;
  private LocalDateTime createdAt;
}
