package com.example.music.service;

import com.example.music.dto.PaymentRequestDto;
import com.example.music.dto.PaymentResponseDto;
import com.example.music.entity.Pass;
import com.example.music.entity.Payment;
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

    @Transactional
    public PaymentResponseDto processPayment(PaymentRequestDto requestDto) {
        // 1. 결제 정보 검증 및 PG사 승인 로직 (여기서는 모의/성공 가정)
        log.info("[Payment] 결제 요청 승인 중 - User: {}, Amount: {}", requestDto.getUserId(), requestDto.getAmount());

        // 2. 결제 내역 저장
        Payment payment = Payment.builder()
                .userId(requestDto.getUserId())
                .orderId(requestDto.getOrderId())
                .paymentKey(requestDto.getPaymentKey())
                .amount(requestDto.getAmount())
                .passType(requestDto.getPassType())
                .status("DONE")
                .paidAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);

        // 3. 결제 완료 시 유저에게 이용권(Pass) 발급 (예: 1개월 이용권)
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime expireDate = startDate.plusMonths(1); // 1개월 뒤 만료

        Pass pass = Pass.builder()
                .userId(requestDto.getUserId())
                .passName(requestDto.getPassType())
                .startDate(startDate)
                .expireDate(expireDate)
                .isActive(true)
                .build();

        passRepository.save(pass);

        log.info("[Payment] ✅ 결제 완료 및 이용권 발급 성공 - User: {}, ExpireDate: {}", requestDto.getUserId(), expireDate);

        return new PaymentResponseDto(payment.getOrderId(), "SUCCESS", "결제가 정상적으로 완료되었습니다.");
    }
}