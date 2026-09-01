package com.example.music.dto;

import java.time.LocalDateTime;

public record LiveBroadcastResponse(
        Long id,
        String title,
        String broadcaster,
        String status,
        int viewerCount,
        String thumbnailUrl,
        String hlsUrl,
        LocalDateTime startedAt
) {
}