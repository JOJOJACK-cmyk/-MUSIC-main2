package com.example.music.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LiveViewerService {

    private static final String KEY_PREFIX = "live:viewers:";

    // 15초 동안 heartbeat가 없으면 시청 종료로 판단
    private static final long VIEWER_TIMEOUT_MS = 15_000;

    private final StringRedisTemplate redisTemplate;

    /**
     * 시청자가 현재 방송을 보고 있다는 heartbeat 기록
     */
    public void heartbeat(Long broadcastId, String viewerId) {

        String key = KEY_PREFIX + broadcastId;

        long now = System.currentTimeMillis();

        // viewerId를 member로,
        // 현재 시간을 score로 저장
        redisTemplate.opsForZSet().add(
                key,
                viewerId,
                now
        );

        removeExpiredViewers(key);

        // 아무도 보지 않게 된 키는 나중에 자동 삭제
        redisTemplate.expire(
                key,
                Duration.ofMinutes(1)
        );
    }

    /**
     * 현재 살아있는 시청자 수
     */
    public int getViewerCount(Long broadcastId) {

        String key = KEY_PREFIX + broadcastId;

        removeExpiredViewers(key);

        Long count =
                redisTemplate.opsForZSet().zCard(key);

        if (count == null) {
            return 0;
        }

        return count.intValue();
    }

    /**
     * 일정 시간 heartbeat가 없는 시청자 제거
     */
    private void removeExpiredViewers(String key) {

        long expiredTime =
                System.currentTimeMillis()
                        - VIEWER_TIMEOUT_MS;

        redisTemplate
                .opsForZSet()
                .removeRangeByScore(
                        key,
                        Double.NEGATIVE_INFINITY,
                        expiredTime
                );
    }
}