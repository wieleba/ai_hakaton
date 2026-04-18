package com.hackathon.shared.dto;

import java.util.UUID;

public record MessagePreview(UUID id, String authorUsername, String textPreview) {
  public static final int PREVIEW_CHARS = 100;
  public static final String DELETED_PLACEHOLDER = "[deleted]";
}
