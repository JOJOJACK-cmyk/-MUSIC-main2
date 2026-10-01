package com.example.music.controller;

import com.example.music.dto.ChatMessageDto;
import com.example.music.entity.Donation;
import com.example.music.entity.User;
import com.example.music.security.AuthenticatedUserResolver;
import com.example.music.service.DonationService;
import com.example.music.service.TossPaymentClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Note API", description = "음표 — 라이브 방송자 후원 (1음표 = 1원)")
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class DonationController {

    private final DonationService donationService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final SimpMessageSendingOperations messagingTemplate;

    @Operation(summary = "음표 주문 만들기", description = "body: {broadcastId, amount, message}. 결제 전에 호출해 orderId 를 받는다.")
    @PostMapping
    public Map<String, Object> prepare(@RequestBody Map<String, Object> body, Authentication authentication) {
        User me = authenticatedUserResolver.resolveRequiredUser(authentication);
        return donationService.prepare(me, toLong(body.get("broadcastId")), toInt(body.get("amount")),
                body.get("message") == null ? null : String.valueOf(body.get("message")));
    }

    @Operation(summary = "음표 결제 승인", description = "토스 결제 성공 후 {paymentKey, orderId, amount} 로 호출. 승인되면 채팅에 음표 알림.")
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody Map<String, Object> body, Authentication authentication) {
        User me = authenticatedUserResolver.resolveRequiredUser(authentication);
        DonationService.ConfirmResult result;
        try {
            result = donationService.confirm(me,
                    body.get("paymentKey") == null ? null : String.valueOf(body.get("paymentKey")),
                    body.get("orderId") == null ? null : String.valueOf(body.get("orderId")),
                    toInt(body.get("amount")));
        } catch (TossPaymentClient.TossPaymentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }

        Donation d = result.donation();
        Long broadcastId = d.getBroadcast().getId();
        if (result.newlyPaid()) {
            // 채팅창 + OBS 오버레이에 음표 알림 (클라이언트는 DONATION 타입을 보낼 수 없음 — 서버만 발송)
            messagingTemplate.convertAndSend("/sub/chat/room/" + broadcastId,
                    ChatMessageDto.builder()
                            .messageId(UUID.randomUUID().toString())
                            .roomId(String.valueOf(broadcastId))
                            .type(ChatMessageDto.MessageType.DONATION)
                            .sender(AuthenticatedUserResolver.displayName(me))
                            .senderId(me.getId())
                            .amount(d.getAmount())
                            .message(d.getMessage())
                            .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                            .build());
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "broadcastId", broadcastId, "amount", d.getAmount()));
    }

    @Operation(summary = "받은 음표", description = "방송자 본인이 받은 음표 최근 50건과 누적 개수")
    @GetMapping("/received")
    public Map<String, Object> received(Authentication authentication) {
        return donationService.received(authenticatedUserResolver.resolveRequiredUser(authentication));
    }

    private static Long toLong(Object o) {
        try { return o == null ? null : Long.valueOf(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }

    private static Integer toInt(Object o) {
        try { return o == null ? null : Integer.valueOf(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }
}
