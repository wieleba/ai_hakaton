package com.hackathon.shared.dto;

import java.util.UUID;

public record EmbedDto(
        UUID id,
        String kind,
        String canonicalId,
        String sourceUrl,
        String title,
        String thumbnailUrl) {}
