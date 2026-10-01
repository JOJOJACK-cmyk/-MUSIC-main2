package com.example.music.controller;

import com.example.music.dto.PaymentConfirmDto;
import com.example.music.dto.PaymentResponseDto;
import com.example.music.entity.Pass;
import com.example.music.entity.User;
import com.example.music.security.AuthenticatedUserResolver;
import com.example.music.service.PassEntitlementService;
import com.example.music.service.PassFeature;
import com.example.music.service.PaymentService;
import com.example.music.service.PricingPlan;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Tag(name = "Payment API", description = "스트리밍 이용권 결제 및 검증 API")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final com.example.music.repository.PaymentRepository paymentRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final PassEntitlementService entitlementService;
    private final com.example.music.repository.MusicRepository musicRepository;

    @Value("${toss.payments.client-key}")
    private String tossClientKey;

    @Operation(summary = "결제 위젯 설정 조회", description = "프론트 토스 결제위젯 초기화에 필요한 클라이언트 키를 반환합니다.")
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> body = new HashMap<>();
        body.put("clientKey", tossClientKey);
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "요금제 목록", description = "요금제별 가격 · 기간 · 포함 기능. 결제 화면이 이 목록을 그대로 보여 준다 (로그인 불필요).")
    @GetMapping("/plans")
    public ResponseEntity<List<Map<String, Object>>> plans() {
        List<Map<String, Object>> body = java.util.Arrays.stream(PricingPlan.values()).map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getPlanId());
            m.put("name", p.getPassName());
            m.put("amount", p.getAmount());
            m.put("months", p.getMonths());
            m.put("songLimit", p.getSongLimit());
            m.put("storeDiscountPct", p.getStoreDiscountPct());
            m.put("features", p.getFeatures().stream().map(Enum::name).toList());
            m.put("highlights", p.getHighlights());
            return m;
        }).toList();
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "내 이용권(구독) 상태 조회",
            description = "지금 쓸 수 있는 기능(features), 이용 중인 · 예약된 이용권, 곡 수 제한 이용권의 사용량을 반환합니다. "
                    + "관리자 · 부관리자는 모든 기능(staff=true).")
    @GetMapping("/subscription")
    public ResponseEntity<Map<String, Object>> mySubscription(Authentication authentication) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        PassEntitlementService.Entitlement e = entitlementService.of(user);

        // 화면 표시용 대표 이용권: 가장 높은 등급(기능이 많은 것), 같으면 만료가 늦은 것
        Optional<Pass> main = e.current().stream().max(java.util.Comparator
                .comparingInt((Pass p) -> PassEntitlementService.planOf(p).map(pl -> pl.getFeatures().size()).orElse(0))
                .thenComparing(Pass::getExpireDate));

        Map<String, Object> body = new HashMap<>();
        body.put("active", !e.current().isEmpty());
        body.put("staff", e.staff());
        body.put("features", e.staff()
                ? java.util.Arrays.stream(PassFeature.values()).map(Enum::name).toList()
                : e.features().stream().map(Enum::name).toList());
        body.put("passName", main.map(Pass::getPassName).orElse(null));
        body.put("planId", main.flatMap(PassEntitlementService::planOf).map(PricingPlan::getPlanId).orElse(null));
        body.put("startDate", main.map(Pass::getStartDate).orElse(null));
        body.put("expireDate", main.map(Pass::getExpireDate).orElse(null));
        body.put("storeDiscountPct", e.storeDiscountPct());
        body.put("passes", e.current().stream().map(PaymentController::passView).toList());
        body.put("upcoming", e.upcoming().stream().map(PaymentController::passView).toList());
        if (e.limitedPass() != null && !e.has(PassFeature.UNLIMITED_PLAY)) {
            List<Long> claimed = entitlementService.claimedSongIds(e);
            body.put("songLimit", e.songLimit());
            body.put("songsUsed", claimed.size());
            body.put("claimedSongIds", claimed);
        }
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> passView(Pass p) {
        Map<String, Object> m = new HashMap<>();
        m.put("passName", p.getPassName());
        m.put("planId", PassEntitlementService.planOf(p).map(PricingPlan::getPlanId).orElse(null));
        m.put("startDate", p.getStartDate());
        m.put("expireDate", p.getExpireDate());
        return m;
    }

    public record PlayClaimRequest(Long musicId) {}

    @Operation(summary = "전곡 재생 확인 · 곡 차감",
            description = "곡 수 제한 이용권이면 이 곡을 1곡 차감하고(같은 곡은 다시 차감 안 함) 전곡 재생 가능 여부를 돌려준다. "
                    + "무제한 이용권 · 관리자는 항상 allowed. 플레이어가 30초 재생 시점에 호출한다.")
    @PostMapping("/play-claim")
    public ResponseEntity<PassEntitlementService.ClaimResult> claimPlay(@RequestBody PlayClaimRequest req,
                                                                        Authentication authentication) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        if (req == null || req.musicId() == null || !musicRepository.existsById(req.musicId())) {
            throw new IllegalArgumentException("곡을 찾을 수 없습니다.");
        }
        return ResponseEntity.ok(entitlementService.claimSong(user, req.musicId()));
    }

    @Operation(summary = "내 결제 내역", description = "현재 로그인 사용자의 결제 내역을 최신순으로 반환합니다.")
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> myPaymentHistory(Authentication authentication) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        List<Map<String, Object>> body = paymentRepository.findByUser_IdOrderByPaidAtDesc(user.getId())
                .stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("orderId", p.getOrderId());
                    m.put("passType", p.getPassType());
                    m.put("amount", p.getAmount());
                    m.put("status", p.getStatus());
                    m.put("paidAt", p.getPaidAt());
                    return m; // paymentKey 는 노출하지 않는다
                })
                .toList();
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
