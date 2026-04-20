package com.hackathon.shared.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessageDTO {
  private UUID id;
  private UUID conversationId;
  private UUID senderId;
  private String senderUsername;
  private String text;
  private OffsetDateTime createdAt;
  private OffsetDateTime editedAt;
  private OffsetDateTime deletedAt;
  private UUID deletedBy;
  private MessagePreview replyTo;
  private List<ReactionSummary> reactions;
  private AttachmentSummary attachment;
  private List<EmbedDto> embeds;
}
