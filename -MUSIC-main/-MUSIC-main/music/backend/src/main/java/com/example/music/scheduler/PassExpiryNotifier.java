package com.example.music.scheduler;

import com.example.music.entity.Pass;
import com.example.music.repository.PassRepository;
import com.example.music.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 이용권 만료 3일 전 개인 알림.
 *  - 매일 오전 10시 + 서버 기동 1분 후 한 번 (서버가 10시에 꺼져 있었어도 놓치지 않게)
 *  - 이미 더 긴 이용권으로 연장한 사용자는 제외
 *  - 같은 이용권에는 한 번만 알림 (Redis pass:expiry-notified:{passId})
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassExpiryNotifier {

    private static final int NOTICE_DAYS_BEFORE = 3;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M월 d일");

    private final PassRepository passRepository;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    public void daily() {
        notifyExpiringPasses();
    }

    @Scheduled(initialDelay = 60_000, fixedRate = Long.MAX_VALUE)
    public void onStartup() {
        notifyExpiringPasses();
    }

    public int notifyExpiringPasses() {
        LocalDateTime now = LocalDateTime.now();
        List<Pass> expiring = passRepository.findByIsActiveTrueAndExpireDateBetween(
                now, now.plusDays(NOTICE_DAYS_BEFORE));

        int sent = 0;
        for (Pass pass : expiring) {
            Long userId = pass.getUser().getId();
            // 이 이용권 뒤로 이어지는(연장된) 이용권이 있으면 알릴 필요 없음
            if (passRepository.existsByUser_IdAndIsActiveTrueAndExpireDateAfter(userId, pass.getExpireDate())) {
                continue;
            }
            String flagKey = "pass:expiry-notified:" + pass.getId();
            try {
                Boolean first = redisTemplate.opsForValue().setIfAbsent(flagKey, "1", Duration.ofDays(7));
                if (!Boolean.TRUE.equals(first)) continue;
            } catch (Exception e) {
                log.warn("[PassExpiry] Redis 사용 불가 - 중복 방지를 할 수 없어 이번 회차 건너뜀: {}", e.getMessage());
                return sent;
            }

            long daysLeft = Math.max(0, ChronoUnit.DAYS.between(now.toLocalDate(), pass.getExpireDate().toLocalDate()));
            String when = daysLeft == 0 ? "오늘" : daysLeft + "일 후";
            notificationService.publishToUser(
                    userId,
                    "PASS_EXPIRY",
                    "이용권이 " + when + " 만료돼요",
                    pass.getPassName() + " · " + pass.getExpireDate().format(DATE_FMT) + " 만료 — 연장하면 남은 기간 뒤로 이어집니다",
                    "/payment");
            sent++;
        }
        if (sent > 0) log.info("[PassExpiry] 만료 임박 알림 {}건 발송", sent);
        return sent;
    }
}
