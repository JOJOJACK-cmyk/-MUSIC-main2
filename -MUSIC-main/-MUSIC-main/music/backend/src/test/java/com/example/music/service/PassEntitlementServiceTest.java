package com.example.music.service;

import com.example.music.dto.PaymentConfirmDto;
import com.example.music.entity.Music;
import com.example.music.entity.Pass;
import com.example.music.entity.User;
import com.example.music.repository.MusicRepository;
import com.example.music.repository.PassRepository;
import com.example.music.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional // 끝나면 롤백 — 실제 DB 에 테스트 데이터를 남기지 않는다
class PassEntitlementServiceTest {

    @Autowired private PassEntitlementService entitlementService;
    @Autowired private PaymentService paymentService;
    @Autowired private ShopService shopService;
    @Autowired private UserRepository userRepository;
    @Autowired private PassRepository passRepository;
    @Autowired private MusicRepository musicRepository;
    @MockitoBean private TossPaymentClient tossPaymentClient;
    @MockitoBean private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate; // 실제로 방송하지 않게
    @Autowired private com.example.music.controller.ChatController chatController;
    @Autowired private SongVoteService songVoteService;

    private User user;
    private String tag;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder().email("pe-" + tag + "@test.com").nickname("pe" + tag)
                .provider("local").role("ROLE_USER").build());
        when(tossPaymentClient.confirm(anyString(), anyString(), anyLong()))
                .thenAnswer(inv -> new TossPaymentClient.TossConfirmResult(inv.getArgument(2), "카드", "이용권", "DONE"));
    }

    private void buy(PricingPlan plan) {
        PaymentConfirmDto dto = new PaymentConfirmDto();
        dto.setPaymentKey("pk-" + UUID.randomUUID());
        dto.setOrderId("order-" + UUID.randomUUID());
        dto.setAmount(plan.getAmount());
        dto.setPlanId(plan.getPlanId());
        paymentService.confirmPayment(user, dto);
    }

    @Test
    void 이용권이_없으면_아무_기능도_없다() {
        PassEntitlementService.Entitlement e = entitlementService.of(user);
        assertThat(e.features()).isEmpty();
        assertThat(entitlementService.claimSong(user, 1L).allowed()).isFalse();
        assertThatThrownBy(() -> entitlementService.require(user, PassFeature.PLAYLIST))
                .isInstanceOf(PassEntitlementService.PassRequiredException.class)
                .hasMessageContaining("스탠다드");
    }

    @Test
    void 관리자는_이용권_없이_모든_기능() {
        User admin = userRepository.save(User.builder().email("pa-" + tag + "@test.com").nickname("pa" + tag)
                .provider("local").role("ROLE_SUB_ADMIN").build());
        PassEntitlementService.Entitlement e = entitlementService.of(admin);
        for (PassFeature f : PassFeature.values()) assertThat(e.has(f)).as(f.name()).isTrue();
        assertThat(entitlementService.claimSong(admin, 1L).unlimited()).isTrue();
    }

    @Test
    void 스탠다드는_무제한_재생과_플레이리스트만() {
        buy(PricingPlan.STANDARD);
        PassEntitlementService.Entitlement e = entitlementService.of(user);
        assertThat(e.has(PassFeature.UNLIMITED_PLAY)).isTrue();
        assertThat(e.has(PassFeature.PLAYLIST)).isTrue();
        assertThat(e.has(PassFeature.CHAT_BADGE)).isFalse();
        assertThat(e.has(PassFeature.STORE_DISCOUNT)).isFalse();
        assertThat(e.storeDiscountPct()).isZero();
    }

    @Test
    void 라이트는_30곡까지만_전곡_재생_같은_곡은_차감없음() {
        buy(PricingPlan.LIGHT);
        assertThat(entitlementService.has(user, PassFeature.PLAYLIST)).isFalse();

        for (long id = 1; id <= 30; id++) {
            PassEntitlementService.ClaimResult r = entitlementService.claimSong(user, -id);
            assertThat(r.allowed()).isTrue();
            assertThat(r.used()).isEqualTo((int) id);
        }
        // 31번째 새 곡은 거부, 이미 들은 곡은 계속 허용 (차감 없음)
        assertThat(entitlementService.claimSong(user, -31L).allowed()).isFalse();
        PassEntitlementService.ClaimResult again = entitlementService.claimSong(user, -5L);
        assertThat(again.allowed()).isTrue();
        assertThat(again.used()).isEqualTo(30);

        assertThat(entitlementService.isFullPlay(user, -5L)).isTrue();
        assertThat(entitlementService.isFullPlay(user, -31L)).isFalse();
    }

    @Test
    void 같은_등급은_만료일_뒤로_이어붙이고_다른_등급은_바로_시작() {
        buy(PricingPlan.STANDARD);
        buy(PricingPlan.STANDARD);
        List<Pass> passes = passRepository.findByUser_IdAndIsActiveTrueAndExpireDateAfterOrderByStartDateAsc(user.getId(), LocalDateTime.now());
        assertThat(passes).hasSize(2);
        assertThat(passes.get(1).getStartDate()).isEqualTo(passes.get(0).getExpireDate()); // 예약
        assertThat(entitlementService.of(user).upcoming()).hasSize(1);

        // 스탠다드 이용 중 프리미엄 결제 → 지금부터 프리미엄 기능
        buy(PricingPlan.PREMIUM);
        PassEntitlementService.Entitlement e = entitlementService.of(user);
        assertThat(e.has(PassFeature.CHAT_BADGE)).isTrue();
        assertThat(e.storeDiscountPct()).isEqualTo(10);

        // 월간 ↔ 연간 프리미엄은 같은 기능이라 이어 붙는다
        buy(PricingPlan.PREMIUM_ANNUAL);
        Pass annual = passRepository.findByUser_IdAndIsActiveTrueAndExpireDateAfterOrderByStartDateAsc(user.getId(), LocalDateTime.now())
                .stream().filter(p -> "premium_annual".equals(p.getPlanId())).findFirst().orElseThrow();
        assertThat(annual.getStartDate()).isAfter(LocalDateTime.now().plusDays(25));
    }

    @Test
    void 예전_이용권은_이름으로_등급을_정한다() {
        passRepository.save(Pass.builder().user(user).passName("무제한 스트리밍 정기 이용권")
                .startDate(LocalDateTime.now().minusDays(1)).expireDate(LocalDateTime.now().plusDays(10)).isActive(true).build());
        PassEntitlementService.Entitlement e = entitlementService.of(user);
        assertThat(e.has(PassFeature.UNLIMITED_PLAY)).isTrue();
        assertThat(e.has(PassFeature.PLAYLIST)).isTrue();
        assertThat(e.has(PassFeature.CHAT_BADGE)).isFalse();
    }

    @Test
    void 프리미엄은_스토어_10퍼센트_할인() {
        Music song = musicRepository.save(Music.builder().youtubeVideoId("pe" + tag).title("t").artist("a").build());
        Long productId = (Long) shopService.createProduct(new ShopService.ProductForm(
                "할인 테스트 " + tag, "설명", 18000, 5, "ALBUM", "zz-" + tag, song.getId(), null)).get("id");
        ShopService.ShippingForm ship = new ShopService.ShippingForm("홍길동", "010-1234-5678", "06236", "서울시", "101호", "");

        assertThat(shopService.prepareOrder(user, productId, 1, ship).get("amount")).isEqualTo(18000);
        buy(PricingPlan.PREMIUM);
        Map<String, Object> order = shopService.prepareOrder(user, productId, 1, ship);
        assertThat(order.get("amount")).isEqualTo(16200);
        assertThat(order.get("discountPct")).isEqualTo(10);
    }

    private com.example.music.dto.ChatMessageDto talk(User u, boolean clientClaimsPremium) {
        com.example.music.dto.ChatMessageDto m = new com.example.music.dto.ChatMessageDto();
        m.setRoomId("-777");
        m.setType(com.example.music.dto.ChatMessageDto.MessageType.TALK);
        m.setMessage("안녕하세요");
        m.setPremium(clientClaimsPremium);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                u.getEmail(), null, java.util.List.of());
        chatController.message(m, auth, "s-" + UUID.randomUUID());
        return m;
    }

    @Test
    void 프리미엄_채팅_배지는_서버가_이용권으로_정한다() {
        // 무료 회원이 premium=true 를 보내도 서버가 false 로 덮는다
        assertThat(talk(user, true).getPremium()).isFalse();
        buy(PricingPlan.PREMIUM);
        assertThat(talk(user, false).getPremium()).isTrue();
    }

    @Test
    void 신청곡_투표는_이용권_없이도_가능() {
        songVoteService.setPollOptions(-777L, List.of(SongVoteService.PollOption.text("곡 A")));
        try {
            assertThat(songVoteService.vote(-777L, ChatController_voterId(user), 0)).isTrue();
        } finally {
            songVoteService.clearBroadcastVotes(-777L);
        }
    }

    private static String ChatController_voterId(User u) {
        return com.example.music.controller.ChatController.voterId(u);
    }
}
