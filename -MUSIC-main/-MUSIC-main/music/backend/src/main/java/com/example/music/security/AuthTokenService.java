package com.example.music.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 불투명(opaque) 액세스 토큰 발급/검증.
 *  - Redis 에 token -> userId 매핑 저장 (7일 TTL)
 *  - Redis 장애 시 인메모리 fallback (개발 편의)
 * JWT 서명 없이도 SPA <-> API 를 stateless 하게 인증할 수 있게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, Long> memoryFallback = new ConcurrentHashMap<>();

    private static final String PREFIX = "auth:token:";
    private static final Duration TTL = Duration.ofDays(7);

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + Long.toHexString(System.nanoTime());
        try {
            redisTemplate.opsForValue().set(PREFIX + token, String.valueOf(userId), TTL);
        } catch (Exception e) {
            log.warn("토큰 Redis 저장 실패 - 인메모리 사용: {}", e.getMessage());
        }
        memoryFallback.put(token, userId);
        return token;
    }

    public Optional<Long> resolve(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            String v = redisTemplate.opsForValue().get(PREFIX + token);
            if (v != null) return Optional.of(Long.valueOf(v));
        } catch (Exception e) {
            log.debug("토큰 Redis 조회 실패: {}", e.getMessage());
        }
        return Optional.ofNullable(memoryFallback.get(token));
    }

    public void revoke(String token) {
        if (token == null) return;
        try { redisTemplate.delete(PREFIX + token); } catch (Exception ignore) {}
        memoryFallback.remove(token);
    }
}
