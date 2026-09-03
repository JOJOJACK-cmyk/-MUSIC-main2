package com.example.music.service;

import com.example.music.dto.PaymentRequestDto;
import com.example.music.dto.PaymentResponseDto;
import com.example.music.entity.Pass;
import com.example.music.entity.Payment;
import com.example.music.entity.User;
import com.example.music.repository.PassRepository;
import com.example.music.repository.PaymentRepository;
import com.example.music.repository.UserRepository;
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
    private final UserRepository userRepository;

    @Transactional
    public PaymentResponseDto processPayment(PaymentRequestDto requestDto) {

        // 1. 실제 사용자 조회 (DTO에 있는 userId 사용)
        User user = userRepository.findById(requestDto.getUserId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 사용자입니다. ID: " + requestDto.getUserId()
                        )
                );

        // 2. 결제 정보 검증 및 승인 로직 수행 로그
        log.info(
                "[Payment] 결제 요청 승인 중 - User ID: {}, Amount: {}, PassType: {}",
                user.getId(),
                requestDto.getAmount(),
                requestDto.getPassType()
        );

        // 3. 결제 내역 저장
        Payment payment = Payment.builder()
                .user(user)
                .orderId(requestDto.getOrderId())
                .paymentKey(requestDto.getPaymentKey())
                .amount(requestDto.getAmount())
                .passType(requestDto.getPassType())
                .status("DONE")
                .paidAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);

        // 4. 이용권 기간 설정 (1개월 기준)
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime expireDate = startDate.plusMonths(1);

        // 5. 이용권 발급 및 저장
        Pass pass = Pass.builder()
                .user(user)
                .passName(requestDto.getPassType())
                .startDate(startDate)
                .expireDate(expireDate)
                .isActive(true)
                .build();

        passRepository.save(pass);

        log.info(
                "[Payment] 결제 완료 및 이용권 발급 성공 - User ID: {}, ExpireDate: {}",
                user.getId(),
                expireDate
        );

        // 6. 응답 DTO 반환
        return new PaymentResponseDto(
                payment.getOrderId(),
                "SUCCESS",
                "결제가 정상적으로 완료되었습니다."
        );
    }
}