package com.example.music.dto;

/**
 * 앱 내 실시간 알림 1건.
 * type: LIVE_START(스트리밍 시작) | NEW_HOT_SONG(새 인기곡) | GENERAL
 * link: 클릭 시 이동할 프론트 경로 (없으면 null)
 * broadcasterId: LIVE_START 일 때 방송 채널 주인의 userId (팔로우 여부 판별용, 없으면 null)
 * createdAt: epoch millis
 */
public record NotificationDto(
        String id,
        String type,
        String title,
        String message,
        String link,
        Long broadcasterId,
        long createdAt
) {
}
