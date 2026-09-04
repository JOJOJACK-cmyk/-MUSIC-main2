package com.example.music.controller;

import com.example.music.dto.PaymentConfirmDto;
import com.example.music.dto.PaymentResponseDto;
import com.example.music.entity.Pass;
import com.example.music.entity.User;
import com.example.music.repository.PassRepository;
import com.example.music.security.AuthenticatedUserResolver;
import com.example.music.service.PaymentService;
import com.example.music.service.TossPaymentClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${toss.payments.client-key}")
    private String tossClientKey;

    @Operation(summary = "결제 위젯 설정 조회", description = "프론트 토스 결제위젯 초기화에 필요한 클라이언트 키를 반환합니다.")
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> body = new HashMap<>();
        body.put("clientKey", tossClientKey);
        return ResponseEntity.ok(body);
    }

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

    @Operation(summary = "결제 승인",
            description = "토스 결제창 성공 후 전달받은 paymentKey/orderId/amount 로 토스 서버 승인을 검증하고, "
                    + "성공하면 이용권을 발급합니다. 승인에 실패하면 이용권은 발급되지 않습니다.")
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmPayment(
            @RequestBody PaymentConfirmDto dto,
            Authentication authentication) {

        User currentUser = authenticatedUserResolver.resolveRequiredUser(authentication);

        try {
            PaymentResponseDto response = paymentService.confirmPayment(currentUser, dto);
            return ResponseEntity.ok(response);
        } catch (TossPaymentClient.TossPaymentException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAIL",
                    "message", e.getMessage()
            ));
        }
    }
}
