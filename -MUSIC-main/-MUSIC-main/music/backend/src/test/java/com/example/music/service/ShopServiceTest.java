package com.example.music.service;

import com.example.music.entity.Music;
import com.example.music.entity.ShopOrder;
import com.example.music.entity.User;
import com.example.music.repository.MusicRepository;
import com.example.music.repository.ProductRepository;
import com.example.music.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class ShopServiceTest {

    @Autowired private ShopService shopService;
    @Autowired private UserRepository userRepository;
    @Autowired private MusicRepository musicRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private EntityManager em;
    @MockitoBean private TossPaymentClient tossPaymentClient;

    private User buyer;
    private User other;
    private Music song;
    private Long albumId;

    private static final ShopService.ShippingForm SHIP =
            new ShopService.ShippingForm("홍길동", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101호", "문 앞");

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        buyer = userRepository.save(User.builder().email("b-" + tag + "@test.com").nickname("b" + tag).provider("local").build());
        other = userRepository.save(User.builder().email("o-" + tag + "@test.com").nickname("o" + tag).provider("local").build());
        song = musicRepository.save(Music.builder().youtubeVideoId("shop" + tag)
                .title("LE SSERAFIM (르세라핌) 'Test' Official MV").artist("HYBE LABELS").build());
        albumId = (Long) shopService.createProduct(new ShopService.ProductForm(
                "테스트 앨범 " + tag, "설명", 20000, 3, "ALBUM", "zz-nomatch-" + tag, song.getId(), null)).get("id");
        when(tossPaymentClient.confirm(anyString(), anyString(), anyLong()))
                .thenReturn(new TossPaymentClient.TossConfirmResult(40000, "카드", "앨범", "DONE"));
    }

    private int stock(Long productId) {
        em.flush();
        em.clear();
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    @Test
    void 주문_결제하면_재고가_줄고_PAID() {
        Map<String, Object> order = shopService.prepareOrder(buyer, albumId, 2, SHIP);
        assertThat(order.get("amount")).isEqualTo(40000); // 서버가 계산한 금액

        ShopService.ConfirmResult r = shopService.confirmOrder(buyer, "pk", (String) order.get("orderId"), 40000);
        assertThat(r.newlyPaid()).isTrue();
        assertThat(r.order().getStatus()).isEqualTo(ShopOrder.PAID);
        assertThat(stock(albumId)).isEqualTo(1);

        // 중복 승인은 다시 처리하지 않음
        assertThat(shopService.confirmOrder(buyer, "pk", (String) order.get("orderId"), 40000).newlyPaid()).isFalse();
        verify(tossPaymentClient, times(1)).confirm(anyString(), anyString(), anyLong());
    }

    @Test
    void 금액_조작_남의_주문_재고초과는_거절() {
        String orderId = (String) shopService.prepareOrder(buyer, albumId, 1, SHIP).get("orderId");
        assertThatThrownBy(() -> shopService.confirmOrder(buyer, "pk", orderId, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shopService.confirmOrder(other, "pk", orderId, 20000))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> shopService.prepareOrder(buyer, albumId, 4, SHIP))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void 배송지_검증() {
        ShopService.ShippingForm badPhone = new ShopService.ShippingForm("홍길동", "12345", "06236", "주소", null, null);
        assertThatThrownBy(() -> shopService.prepareOrder(buyer, albumId, 1, badPhone))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 취소하면_환불되고_재고_복구() {
        String orderId = (String) shopService.prepareOrder(buyer, albumId, 2, SHIP).get("orderId");
        shopService.confirmOrder(buyer, "pk_cancel", orderId, 40000);

        Map<String, Object> cancelled = shopService.cancelMyOrder(buyer, orderId);
        assertThat(cancelled.get("status")).isEqualTo(ShopOrder.CANCELLED);
        verify(tossPaymentClient).cancel(eq("pk_cancel"), anyString());
        assertThat(stock(albumId)).isEqualTo(3);
    }

    @Test
    void 배송_시작된_주문은_본인이_취소_불가_관리자_흐름() {
        String orderId = (String) shopService.prepareOrder(buyer, albumId, 1, SHIP).get("orderId");
        shopService.confirmOrder(buyer, "pk", orderId, 20000);
        shopService.adminUpdateStatus(orderId, "SHIPPING");

        assertThatThrownBy(() -> shopService.cancelMyOrder(buyer, orderId)).isInstanceOf(IllegalArgumentException.class);
        assertThat(shopService.adminUpdateStatus(orderId, "DELIVERED").get("status")).isEqualTo(ShopOrder.DELIVERED);
        assertThatThrownBy(() -> shopService.adminUpdateStatus(orderId, "PAID")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 관련_상품은_곡_연결과_아티스트_별칭으로_찾는다() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Long merchId = (Long) shopService.createProduct(new ShopService.ProductForm(
                "응원봉 " + tag, null, 30000, 10, "MERCH", "르세라핌, LE SSERAFIM", null, null)).get("id");

        List<Map<String, Object>> related = shopService.relatedProducts(song.getId());
        List<Object> ids = related.stream().map(m -> m.get("id")).toList();
        assertThat(ids).contains(albumId, merchId);
        assertThat(ids.indexOf(albumId)).isLessThan(ids.indexOf(merchId)); // 곡에 직접 연결된 상품이 먼저

        shopService.setProductActive(merchId, false);
        assertThat(shopService.relatedProducts(song.getId()).stream().map(m -> m.get("id")).toList())
                .doesNotContain(merchId);
    }
}
