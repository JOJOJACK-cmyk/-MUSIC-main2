package com.example.music.service;

import com.example.music.entity.Broadcast;
import com.example.music.entity.Donation;
import com.example.music.entity.User;
import com.example.music.repository.BroadcastRepository;
import com.example.music.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class DonationServiceTest {

    @Autowired private DonationService donationService;
    @Autowired private UserRepository userRepository;
    @Autowired private BroadcastRepository broadcastRepository;
    @MockitoBean private TossPaymentClient tossPaymentClient; // 실제 토스 호출 대신

    private User viewer;
    private User streamer;
    private Broadcast live;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        viewer = userRepository.save(User.builder().email("v-" + tag + "@test.com").nickname("v" + tag).provider("local").build());
        streamer = userRepository.save(User.builder().email("s-" + tag + "@test.com").nickname("s" + tag).provider("local").build());
        live = new Broadcast();
        live.setUser(streamer);
        live.setTitle("테스트 방송");
        live.setStatus("ON");
        live.setStreamKey("live_test_" + tag);
        live.setPlaybackId("pb_test_" + tag);
        live.setCreatedAt(LocalDateTime.now());
        live = broadcastRepository.save(live);
        when(tossPaymentClient.confirm(anyString(), anyString(), anyLong()))
                .thenReturn(new TossPaymentClient.TossConfirmResult(5000, "카드", "음표", "DONE"));
    }

    @Test
    void 음표_주문_후_승인하면_DONE_이고_중복_승인은_새로_처리하지_않는다() {
        Map<String, Object> order = donationService.prepare(viewer, live.getId(), 5000, "  응원해요!  ");
        String orderId = (String) order.get("orderId");
        assertThat(order.get("amount")).isEqualTo(5000);

        DonationService.ConfirmResult first = donationService.confirm(viewer, "pk_1", orderId, 5000);
        assertThat(first.newlyPaid()).isTrue();
        assertThat(first.donation().getStatus()).isEqualTo(Donation.DONE);
        assertThat(first.donation().getMessage()).isEqualTo("응원해요!");

        DonationService.ConfirmResult again = donationService.confirm(viewer, "pk_1", orderId, 5000);
        assertThat(again.newlyPaid()).isFalse();
        verify(tossPaymentClient, times(1)).confirm(anyString(), anyString(), anyLong());

        Map<String, Object> received = donationService.received(streamer);
        assertThat(received.get("total")).isEqualTo(5000L);
    }

    @Test
    void 금액을_바꿔서_승인하면_거절() {
        String orderId = (String) donationService.prepare(viewer, live.getId(), 5000, null).get("orderId");
        assertThatThrownBy(() -> donationService.confirm(viewer, "pk", orderId, 100))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void 남의_주문은_승인할_수_없다() {
        String orderId = (String) donationService.prepare(viewer, live.getId(), 5000, null).get("orderId");
        assertThatThrownBy(() -> donationService.confirm(streamer, "pk", orderId, 5000))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 금액_범위_방송상태_본인방송_검사() {
        assertThatThrownBy(() -> donationService.prepare(viewer, live.getId(), 500, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> donationService.prepare(streamer, live.getId(), 5000, null))
                .isInstanceOf(IllegalArgumentException.class);
        live.setStatus("OFF");
        assertThatThrownBy(() -> donationService.prepare(viewer, live.getId(), 5000, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
