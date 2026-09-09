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
        String category,
        String snapshotUrl,
        boolean songRequestEnabled
) {
    // 하위호환: broadcasterId / category 없이 만들던 기존 호출부 대응
    public LiveBroadcastResponse(Long id, String title, String broadcaster, String status,
                                 int viewerCount, String thumbnailUrl, String hlsUrl, LocalDateTime startedAt) {
        this(id, title, broadcaster, null, status, viewerCount, thumbnailUrl, hlsUrl, startedAt, null, null, false);
    }

    // 하위호환: snapshotUrl / songRequestEnabled 없이 만들던 호출부 대응
    public LiveBroadcastResponse(Long id, String title, String broadcaster, Long broadcasterId, String status,
                                 int viewerCount, String thumbnailUrl, String hlsUrl, LocalDateTime startedAt,
                                 String category) {
        this(id, title, broadcaster, broadcasterId, status, viewerCount, thumbnailUrl, hlsUrl, startedAt, category, null, false);
    }
}
