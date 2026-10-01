package com.example.music.scheduler;

import com.example.music.repository.DonationRepository;
import com.example.music.repository.ShopOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 결제창을 열었다가 그만둔 음표/스토어 주문(PENDING)은 결제 승인 없이 계속 쌓이므로 하루가 지나면 지운다.
 * (토스 결제창 세션은 그보다 훨씬 짧아 하루 지난 PENDING 이 나중에 승인될 일은 없다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingPaymentCleaner {

    private final DonationRepository donationRepository;
    private final ShopOrderRepository shopOrderRepository;

    @Scheduled(initialDelay = 5 * 60_000, fixedDelay = 60 * 60_000)
    public void cleanup() {
        LocalDateTime before = LocalDateTime.now().minusDays(1);
        try {
            int notes = donationRepository.deletePendingBefore(before);
            int orders = shopOrderRepository.deletePendingBefore(before);
            if (notes + orders > 0) {
                log.info("[정리] 결제 대기 주문 삭제 - 음표 {}건, 스토어 {}건", notes, orders);
            }
        } catch (Exception e) {
            log.warn("[정리] 결제 대기 주문 정리 실패: {}", e.getMessage());
        }
    }
}
