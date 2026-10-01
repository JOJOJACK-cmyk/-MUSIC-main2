package com.example.music.scheduler;

import com.example.music.entity.Pass;
import com.example.music.entity.User;
import com.example.music.repository.PassRepository;
import com.example.music.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PassExpiryNotifierTest {

    private PassRepository passRepository;
    private NotificationService notificationService;
    private ValueOperations<String, String> valueOps;
    private PassExpiryNotifier notifier;
    private Pass pass;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        passRepository = mock(PassRepository.class);
        notificationService = mock(NotificationService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        notifier = new PassExpiryNotifier(passRepository, notificationService, redis);

        User user = User.builder().email("a@test.com").nickname("a").role("ROLE_USER").build();
        ReflectionTestUtils.setField(user, "id", 7L);
        pass = Pass.builder().user(user).passName("1개월 이용권")
                .startDate(LocalDateTime.now().minusDays(28))
                .expireDate(LocalDateTime.now().plusDays(2))
                .isActive(true).build();
        ReflectionTestUtils.setField(pass, "id", 100L);
        when(passRepository.findByIsActiveTrueAndExpireDateBetween(any(), any())).thenReturn(List.of(pass));
    }

    @Test
    void 만료_임박_이용권에_한번만_알림() {
        when(passRepository.existsByUser_IdAndIsActiveTrueAndExpireDateAfter(eq(7L), any())).thenReturn(false);
        when(valueOps.setIfAbsent(eq("pass:expiry-notified:100"), anyString(), any(java.time.Duration.class))).thenReturn(true, false);

        assertThat(notifier.notifyExpiringPasses()).isEqualTo(1);
        assertThat(notifier.notifyExpiringPasses()).isZero(); // 두 번째는 중복 방지

        verify(notificationService, times(1))
                .publishToUser(eq(7L), eq("PASS_EXPIRY"), contains("2일 후"), anyString(), eq("/payment"));
    }

    @Test
    void 이미_연장한_사용자는_제외() {
        when(passRepository.existsByUser_IdAndIsActiveTrueAndExpireDateAfter(eq(7L), any())).thenReturn(true);

        assertThat(notifier.notifyExpiringPasses()).isZero();
        verifyNoInteractions(notificationService);
    }
}
