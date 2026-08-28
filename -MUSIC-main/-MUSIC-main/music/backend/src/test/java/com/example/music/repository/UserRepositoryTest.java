package com.example.music.repository;

import com.example.music.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveUserTest() {
        String uniqueEmail = "test_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        User user = User.builder()
                .email(uniqueEmail)
                .password("1234")
                .nickname("테스트회원")
                .role("USER")
                .build();

        User savedUser = userRepository.save(user);

        System.out.println("회원 번호 = " + savedUser.getId());
        System.out.println("이메일 = " + savedUser.getEmail());
        System.out.println("닉네임 = " + savedUser.getNickname());
    }
}