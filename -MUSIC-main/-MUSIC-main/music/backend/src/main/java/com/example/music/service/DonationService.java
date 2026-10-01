package com.example.music.service;

import com.example.music.entity.Broadcast;
import com.example.music.entity.Donation;
import com.example.music.entity.User;
import com.example.music.repository.BroadcastRepository;
import com.example.music.repository.DonationRepository;
import com.example.music.security.AuthenticatedUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 음표 — 라이브 방송자에게 보내는 후원. 1음표 = 1원.
 *  1) prepare : 서버가 금액·받는 사람을 고정한 PENDING 주문 생성 → orderId 반환
 *  2) 토스 결제창
 *  3) confirm : 서버 금액과 일치할 때만 토스 승인 → DONE (호출측이 채팅에 음표 알림 발송)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DonationService {

    public static final int MIN_NOTES = 1_000;
    public static final int MAX_NOTES = 500_000;
    private static final int MAX_MESSAGE_LENGTH = 100;

    private final DonationRepository donationRepository;
    private final BroadcastRepository broadcastRepository;
    private final TossPaymentClient tossPaymentClient;

    /** confirm 결과 — 새로 승인된 경우에만 채팅 알림을 보내기 위해 구분 */
    public record ConfirmResult(Donation donation, boolean newlyPaid) {}

    @Transactional
    public Map<String, Object> prepare(User donor, Long broadcastId, Integer amount, String message) {
        if (broadcastId == null) throw new IllegalArgumentException("방송 정보가 없습니다.");
        if (amount == null || amount < MIN_NOTES || amount > MAX_NOTES) {
            throw new IllegalArgumentException("음표는 " + fmt(MIN_NOTES) + "개부터 " + fmt(MAX_NOTES) + "개까지 보낼 수 있어요.");
        }
        Broadcast broadcast = broadcastRepository.findWithUserById(broadcastId)
                .orElseThrow(() -> new IllegalArgumentException("방송을 찾을 수 없습니다."));
        if (!"ON".equalsIgnoreCase(broadcast.getStatus())) {
            throw new IllegalArgumentException("방송 중일 때만 음표를 보낼 수 있어요.");
        }
        if (broadcast.getUser().getId().equals(donor.getId())) {
            throw new IllegalArgumentException("내 방송에는 음표를 보낼 수 없어요.");
        }

        String cleanMessage = message == null ? null : message.trim();
        if (cleanMessage != null && cleanMessage.isEmpty()) cleanMessage = null;
        if (cleanMessage != null && cleanMessage.length() > MAX_MESSAGE_LENGTH) {
            cleanMessage = cleanMessage.substring(0, MAX_MESSAGE_LENGTH);
        }

        String orderId = "note_" + UUID.randomUUID().toString().replace("-", "");
        donationRepository.save(new Donation(orderId, donor, broadcast, amount, cleanMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("amount", amount);
        body.put("orderName", "음표 " + fmt(amount) + "개 · " + AuthenticatedUserResolver.displayName(broadcast.getUser()));
        return body;
    }

    @Transactional
    public ConfirmResult confirm(User donor, String paymentKey, String orderId, Integer amount) {
        if (paymentKey == null || orderId == null || amount == null) {
            throw new IllegalArgumentException("결제 정보가 올바르지 않습니다.");
        }
        Donation donation = donationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("음표 주문을 찾을 수 없습니다."));
        if (!donation.getDonor().getId().equals(donor.getId())) {
            throw new AccessDeniedException("본인의 음표 주문만 승인할 수 있습니다.");
        }
        if (Donation.DONE.equals(donation.getStatus())) {
            return new ConfirmResult(donation, false); // 새로고침 등 중복 승인
        }
        if (donation.getAmount() != amount) {
            throw new IllegalArgumentException("결제 금액이 음표 주문과 일치하지 않습니다.");
        }

        tossPaymentClient.confirm(paymentKey, orderId, donation.getAmount()); // 실패 시 예외 → 롤백
        donation.markPaid(paymentKey);
        log.info("[음표] donor={} → recipient={} broadcast={} amount={}",
                donor.getId(), donation.getRecipient().getId(), donation.getBroadcast().getId(), donation.getAmount());
        return new ConfirmResult(donation, true);
    }

    /** 방송자가 받은 음표 (최근 50건 + 누적) */
    @Transactional(readOnly = true)
    public Map<String, Object> received(User recipient) {
        List<Map<String, Object>> items = donationRepository
                .findTop50ByRecipient_IdAndStatusOrderByPaidAtDesc(recipient.getId(), Donation.DONE)
                .stream()
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("donor", AuthenticatedUserResolver.displayName(d.getDonor()));
                    m.put("amount", d.getAmount());
                    m.put("message", d.getMessage());
                    m.put("paidAt", d.getPaidAt());
                    return m;
                })
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("total", donationRepository.sumReceived(recipient.getId()));
        body.put("items", items);
        return body;
    }

    private static String fmt(int n) {
        return NumberFormat.getInstance(Locale.KOREA).format(n);
    }
}
