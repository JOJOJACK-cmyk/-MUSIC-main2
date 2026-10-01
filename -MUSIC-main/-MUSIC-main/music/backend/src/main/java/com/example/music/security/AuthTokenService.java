package com.example.music.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 불투명(opaque) 액세스 토큰 발급/검증.
 *  - Redis 에 token -> userId 매핑 저장 (7일 TTL)
 *  - Redis 장애 시에만 인메모리 fallback (만료 시각 포함, 개발 편의)
 * JWT 서명 없이도 SPA <-> API 를 stateless 하게 인증할 수 있게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final StringRedisTemplate redisTemplate;

    /** Redis 저장이 실패한 토큰만 담는다. 만료 시각을 함께 들고 있어 TTL 이 지나면 무효. */
    private final ConcurrentHashMap<String, MemoryEntry> memoryFallback = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private static final String PREFIX = "auth:token:";
    private static final Duration TTL = Duration.ofDays(7);

    private record MemoryEntry(Long userId, long expiresAtMillis) {
        boolean expired() { return System.currentTimeMillis() > expiresAtMillis; }
    }

    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        try {
            redisTemplate.opsForValue().set(PREFIX + token, String.valueOf(userId), TTL);
        } catch (Exception e) {
            log.warn("토큰 Redis 저장 실패 - 인메모리 사용: {}", e.getMessage());
            memoryFallback.put(token, new MemoryEntry(userId, System.currentTimeMillis() + TTL.toMillis()));
        }
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
        MemoryEntry entry = memoryFallback.get(token);
        if (entry == null) return Optional.empty();
        if (entry.expired()) {
            memoryFallback.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        try { redisTemplate.delete(PREFIX + token); } catch (Exception ignore) {}
        memoryFallback.remove(token);
    }
}
