package com.example.music.dto;

import java.time.LocalDateTime;

public record LiveBroadcastResponse(
        Long id,
        String title,
        String broadcaster,
        Long broadcasterId,
        String status,
        int viewerCount,
        String thumbnailUrl,
        String hlsUrl,
        LocalDateTime startedAt,
        String category
) {
    // 하위호환: broadcasterId / category 없이 만들던 기존 호출부 대응
    public LiveBroadcastResponse(Long id, String title, String broadcaster, String status,
                                 int viewerCount, String thumbnailUrl, String hlsUrl, LocalDateTime startedAt) {
        this(id, title, broadcaster, null, status, viewerCount, thumbnailUrl, hlsUrl, startedAt, null);
    }
}