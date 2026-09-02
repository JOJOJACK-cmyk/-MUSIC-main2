package com.example.music.controller;

import com.example.music.dto.PaymentRequestDto;
import com.example.music.dto.PaymentResponseDto;
import com.example.music.service.PaymentService;
import com.example.music.security.AuthenticatedUserResolver; // 추가
import com.example.music.entity.User; // 추가
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // 추가
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthenticatedUserResolver authenticatedUserResolver; // 주입 받기

    @PostMapping("/request")
    public ResponseEntity<PaymentResponseDto> requestPayment(
            @RequestBody PaymentRequestDto requestDto,
            Authentication authentication) { // Spring Security의 Authentication 객체 주입

        // 1. Resolver를 통해 현재 로그인한 User 엔티티 안전하게 획득
        User currentUser = authenticatedUserResolver.resolveRequiredUser(authentication);

        // 2. 만약 DTO에 userId가 비어있거나 서버에서 강제로 매칭해야 한다면 세팅
        // (Service 구조에 맞춰서 처리)
        // requestDto.setUserId(currentUser.getId());

        PaymentResponseDto response = paymentService.processPayment(requestDto);
        return ResponseEntity.ok(response);
    }
}