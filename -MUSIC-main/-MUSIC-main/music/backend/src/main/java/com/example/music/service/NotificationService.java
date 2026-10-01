package com.example.music.service;

import com.example.music.dto.NotificationDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 앱 내 실시간 알림 발행/조회.
 *  - 발행: STOMP 로 /topic/notifications 브로드캐스트 (모든 접속자에게)
 *  - 이력: Redis 리스트에 최근 N건 보관 (헤더 벨 목록용). Redis 장애 시에도 브로드캐스트는 계속됨.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String RECENT_KEY = "notifications:recent";
    private static final int MAX_RECENT = 30;

    public NotificationDto publish(String type, String title, String message, String link) {
        return publish(type, title, message, link, null);
    }

    public NotificationDto publish(String type, String title, String message, String link, Long broadcasterId) {
        NotificationDto dto = new NotificationDto(
                UUID.randomUUID().toString(), type, title, message, link, broadcasterId, System.currentTimeMillis());

        try {
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForList().leftPush(RECENT_KEY, json);
            redisTemplate.opsForList().trim(RECENT_KEY, 0, MAX_RECENT - 1);
        } catch (Exception e) {
            log.warn("알림 이력 저장 실패 (무시): {}", e.getMessage());
        }

        try {
            messagingTemplate.convertAndSend("/topic/notifications", dto);
        } catch (Exception e) {
            log.warn("알림 브로드캐스트 실패: {}", e.getMessage());
        }

        log.info("🔔 알림 발행 [{}] {}", type, title);
        return dto;
    }

    /**
     * 특정 사용자에게만 보이는 개인 알림 (예: 이용권 만료 임박).
     * Redis notifications:user:{userId} 에 최근 30건 보관 — 프론트가 로그인 시/주기적으로 조회한다.
     */
    public NotificationDto publishToUser(Long userId, String type, String title, String message, String link) {
        NotificationDto dto = new NotificationDto(
                UUID.randomUUID().toString(), type, title, message, link, null, System.currentTimeMillis());
        String key = userKey(userId);
        try {
            redisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(dto));
            redisTemplate.opsForList().trim(key, 0, MAX_RECENT - 1);
        } catch (Exception e) {
            log.warn("개인 알림 저장 실패 userId={}: {}", userId, e.getMessage());
        }
        log.info("🔔 개인 알림 [{}] userId={} {}", type, userId, title);
        return dto;
    }

    public List<NotificationDto> getForUser(Long userId) {
        return readList(userKey(userId));
    }

    private static String userKey(Long userId) {
        return "notifications:user:" + userId;
    }

    public List<NotificationDto> getRecent() {
        return readList(RECENT_KEY);
    }

    private List<NotificationDto> readList(String key) {
        List<NotificationDto> result = new ArrayList<>();
        try {
            List<String> raw = redisTemplate.opsForList().range(key, 0, MAX_RECENT - 1);
            if (raw != null) {
                for (String json : raw) {
                    try {
                        result.add(objectMapper.readValue(json, NotificationDto.class));
                    } catch (Exception ignore) {}
                }
            }
        } catch (Exception e) {
            log.warn("알림 이력 조회 실패 (빈 목록 반환): {}", e.getMessage());
        }
        return result;
    }
}
