package com.example.music.service;

import com.example.music.entity.Broadcast;
import com.example.music.entity.User;
import com.example.music.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BroadcastServiceTest {

    @Autowired
    private BroadcastService broadcastService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("스트림 키 발급 및 상태 변경 테스트")
    void testBroadcastWorkflow() {
        // 예시 수정 코드
        String uniqueEmail = "test_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        User user = User.builder()
                .email(uniqueEmail) // 고정값 대신 uniqueEmail 사용
                .nickname("테스트방송인")
                .role("ROLE_USER")
                .build();
        User savedUser = userRepository.save(user);

        // 2. 스트림 키 발급 테스트
        Broadcast broadcast = broadcastService.createOrUpdateStreamKey(savedUser, "테스트 방송 타이틀");

        assertThat(broadcast).isNotNull();
        assertThat(broadcast.getStreamKey()).startsWith("live_");
        assertThat(broadcast.getTitle()).isEqualTo("테스트 방송 타이틀");
        System.out.println("발급된 스트림 키: " + broadcast.getStreamKey());

        // 3. 방송 상태 ON 변경 테스트
        broadcastService.updateBroadcastStatus(savedUser.getId(), "ON");
        // (필요시 리포지토리로 다시 조회해서 status와 startedAt 확인 가능)
    }
}