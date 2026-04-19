package com.hackathon.shared.dto;

import java.util.UUID;

public record AttachmentSummary(UUID id, String filename, String mimeType, long sizeBytes) {}
