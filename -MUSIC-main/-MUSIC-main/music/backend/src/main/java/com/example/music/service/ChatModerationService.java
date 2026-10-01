package com.example.music.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 라이브 채팅 관리 — 방송자가 특정 시청자의 채팅을 일정 시간 금지(뮤트)한다.
 * Redis 키: broadcast:{id}:chat:mute:{userId}  (TTL = 금지 시간)
 */
@Service
public class ChatModerationService {

    /** 허용하는 금지 시간(분). 720분 = 12시간 ≒ 이번 방송 동안 */
    public static final int[] ALLOWED_MINUTES = {10, 60, 720};

    private final StringRedisTemplate redis;

    public ChatModerationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String muteKey(Long broadcastId, Long userId) {
        return "broadcast:" + broadcastId + ":chat:mute:" + userId;
    }

    public static boolean isAllowedMinutes(int minutes) {
        for (int m : ALLOWED_MINUTES) if (m == minutes) return true;
        return false;
    }

    public void mute(Long broadcastId, Long userId, int minutes) {
        redis.opsForValue().set(muteKey(broadcastId, userId), "1", Duration.ofMinutes(minutes));
    }

    public void unmute(Long broadcastId, Long userId) {
        redis.delete(muteKey(broadcastId, userId));
    }

    /** 남은 금지 시간(초). 금지 상태가 아니면 0 */
    public long remainingMuteSeconds(Long broadcastId, Long userId) {
        if (broadcastId == null || userId == null) return 0;
        Long ttl = redis.getExpire(muteKey(broadcastId, userId));
        return ttl == null || ttl < 0 ? 0 : ttl;
    }
}
