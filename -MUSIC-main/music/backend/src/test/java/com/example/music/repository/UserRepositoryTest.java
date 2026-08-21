package com.example.music.repository;

import com.example.music.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveUserTest() {

        User user = new User();

        user.setEmail("test@test.com");
        user.setPassword("1234");
        user.setNickname("테스트회원");
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        System.out.println("회원 번호 = " + savedUser.getId());
        System.out.println("이메일 = " + savedUser.getEmail());
        System.out.println("닉네임 = " + savedUser.getNickname());
    }
}