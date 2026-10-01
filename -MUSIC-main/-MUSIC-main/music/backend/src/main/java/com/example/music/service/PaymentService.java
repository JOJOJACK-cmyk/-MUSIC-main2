package com.example.music.service;

import com.example.music.dto.PaymentConfirmDto;
import com.example.music.dto.PaymentResponseDto;
import com.example.music.entity.Pass;
import com.example.music.entity.Payment;
import com.example.music.entity.User;
import com.example.music.repository.PassRepository;
import com.example.music.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PassRepository passRepository;
    private final TossPaymentClient tossPaymentClient;

    /**
     * 토스 결제창 성공 후 승인 처리.
     *  1) planId 로 서버측 금액/기간 확정
     *  2) 프론트가 보낸 금액이 요금제 금액과 일치하는지 검증
     *  3) 토스 서버에 실제 결제 승인 요청 (여기서 실패하면 이용권 미발급)
     *  4) 결제 내역 저장 + 이용권(tb_pass) 발급
     */
    @Transactional
    public PaymentResponseDto confirmPayment(User user, PaymentConfirmDto dto) {

        PricingPlan plan = PricingPlan.fromPlanId(dto.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 요금제입니다: " + dto.getPlanId()));

        if (dto.getAmount() != plan.getAmount()) {
            throw new IllegalArgumentException("결제 금액이 요금제와 일치하지 않습니다.");
        }

        // 동일 주문번호 중복 승인 방지 (새로고침 등)
        if (paymentRepository.findByOrderId(dto.getOrderId()).isPresent()) {
            log.info("[Payment] 이미 처리된 주문입니다. orderId={}", dto.getOrderId());
            return new PaymentResponseDto(dto.getOrderId(), "SUCCESS", "이미 처리된 결제입니다.");
        }

        // 토스 서버 승인 (실제 결제 검증). 실패 시 예외 → 트랜잭션 롤백, 이용권 미발급.
        TossPaymentClient.TossConfirmResult result =
                tossPaymentClient.confirm(dto.getPaymentKey(), dto.getOrderId(), plan.getAmount());

        log.info("[Payment] 토스 승인 완료 - user={}, orderId={}, amount={}, method={}",
                user.getId(), dto.getOrderId(), result.amount(), result.method());

        savePaymentAndPass(user, dto.getOrderId(), dto.getPaymentKey(),
                (int) result.amount(), plan, result.status());

        return new PaymentResponseDto(dto.getOrderId(), "SUCCESS", "결제가 정상적으로 완료되었습니다.");
    }

    private void savePaymentAndPass(User user, String orderId, String paymentKey,
                                    int amount, PricingPlan plan, String status) {
        Payment payment = Payment.builder()
                .user(user)
                .orderId(orderId)
                .paymentKey(paymentKey)
                .amount(amount)
                .passType(plan.getPassName())
                .status(status)
                .paidAt(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);

        // 같은 기능의 이용권(같은 요금제, 월간↔연간 프리미엄, 예전 이용권 포함)이 남아 있으면
        // 남은 기간을 버리지 않고 그 만료일 뒤로 이어 붙인다 (예약 → 시작일이 오면 이어서 적용).
        // 다른 등급이면 지금부터 바로 시작 — 기능은 지금 쓰는 이용권과 합쳐서 적용된다.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = passRepository
                .findByUser_IdAndIsActiveTrueAndExpireDateAfterOrderByStartDateAsc(user.getId(), now)
                .stream()
                .filter(p -> PassEntitlementService.planOf(p)
                        .map(existing -> existing.getFeatures().equals(plan.getFeatures()))
                        .orElse(false))
                .map(Pass::getExpireDate)
                .max(LocalDateTime::compareTo)
                .orElse(now);
        LocalDateTime expireDate = startDate.plusMonths(plan.getMonths());

        Pass pass = Pass.builder()
                .user(user)
                .passName(plan.getPassName())
                .planId(plan.getPlanId())
                .startDate(startDate)
                .expireDate(expireDate)
                .isActive(true)
                .build();
        passRepository.save(pass);

        log.info("[Payment] 이용권 발급 완료 - user={}, plan={}, expireDate={}",
                user.getId(), plan.name(), expireDate);
    }
}
