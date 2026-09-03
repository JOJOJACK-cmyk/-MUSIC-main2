package com.example.music.controller;

import com.example.music.dto.PaymentRequestDto;
import com.example.music.dto.PaymentResponseDto;
import com.example.music.entity.User;
import com.example.music.security.AuthenticatedUserResolver;
import com.example.music.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment API", description = "스트리밍 이용권 결제 및 검증 API")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Operation(summary = "결제 요청 처리", description = "현재 로그인한 사용자의 인증 정보를 바탕으로 스트리밍 이용권 결제 요청을 처리합니다.")
    @PostMapping("/request")
    public ResponseEntity<PaymentResponseDto> requestPayment(
            @RequestBody PaymentRequestDto requestDto,
            Authentication authentication) {

        // 1. AuthenticatedUserResolver를 통해 현재 로그인한 User 엔티티를 안전하게 획득
        User currentUser = authenticatedUserResolver.resolveRequiredUser(authentication);

        // 2. DTO에 userId가 비어있거나 보안상 클라이언트 조작을 방지하기 위해 서버에서 직접 세팅
        // (PassRequestDto 등에 setUserId가 없다면 DTO에 setter를 추가해 주셔야 합니다)
        requestDto.setUserId(currentUser.getId());

        // 3. 결제 서비스 호출
        PaymentResponseDto response = paymentService.processPayment(requestDto);
        return ResponseEntity.ok(response);
    }
}