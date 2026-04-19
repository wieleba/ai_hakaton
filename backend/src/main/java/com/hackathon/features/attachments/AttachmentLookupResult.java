package com.hackathon.features.attachments;

import java.util.UUID;

public record AttachmentLookupResult(
    UUID attachmentId,
    String filename,
    String mimeType,
    long sizeBytes,
    String storageKey,
    Scope scope,
    UUID scopeId /* roomId or conversationId */) {

  public enum Scope {
    ROOM,
    DIRECT
  }
}
