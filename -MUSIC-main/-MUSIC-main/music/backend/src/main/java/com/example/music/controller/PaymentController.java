package com.example.music.controller;

import com.example.music.dto.PaymentRequestDto;
import com.example.music.dto.PaymentResponseDto;
import com.example.music.entity.Pass;
import com.example.music.entity.User;
import com.example.music.repository.PassRepository;
import com.example.music.security.AuthenticatedUserResolver;
import com.example.music.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Tag(name = "Payment API", description = "스트리밍 이용권 결제 및 검증 API")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PassRepository passRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Operation(summary = "내 이용권(구독) 상태 조회",
            description = "현재 로그인 사용자의 유효한 이용권 여부를 반환합니다. 프론트 플레이어의 미리듣기 제한 해제 판정에 사용합니다.")
    @GetMapping("/subscription")
    public ResponseEntity<Map<String, Object>> mySubscription(Authentication authentication) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        LocalDateTime now = LocalDateTime.now();

        Optional<Pass> pass = passRepository
                .findFirstByUser_IdAndIsActiveTrueAndExpireDateAfterOrderByExpireDateDesc(user.getId(), now);

        Map<String, Object> body = new HashMap<>();
        body.put("active", pass.isPresent());
        body.put("passName", pass.map(Pass::getPassName).orElse(null));
        body.put("startDate", pass.map(Pass::getStartDate).orElse(null));
        body.put("expireDate", pass.map(Pass::getExpireDate).orElse(null));
        return ResponseEntity.ok(body);
    }

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