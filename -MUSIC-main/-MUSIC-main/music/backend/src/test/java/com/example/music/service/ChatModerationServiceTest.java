package com.example.music.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ChatModerationServiceTest {

    private static final Long BROADCAST_ID = -9999L;
    private static final Long USER_ID = -1L;

    @Autowired
    private ChatModerationService chatModerationService;

    @AfterEach
    void cleanup() {
        chatModerationService.unmute(BROADCAST_ID, USER_ID);
    }

    @Test
    void 채팅_금지와_해제() {
        assertThat(chatModerationService.remainingMuteSeconds(BROADCAST_ID, USER_ID)).isZero();

        chatModerationService.mute(BROADCAST_ID, USER_ID, 10);
        assertThat(chatModerationService.remainingMuteSeconds(BROADCAST_ID, USER_ID)).isBetween(590L, 600L);

        chatModerationService.unmute(BROADCAST_ID, USER_ID);
        assertThat(chatModerationService.remainingMuteSeconds(BROADCAST_ID, USER_ID)).isZero();
    }

    @Test
    void 허용된_금지_시간만_통과() {
        assertThat(ChatModerationService.isAllowedMinutes(10)).isTrue();
        assertThat(ChatModerationService.isAllowedMinutes(720)).isTrue();
        assertThat(ChatModerationService.isAllowedMinutes(99999)).isFalse();
    }
}
