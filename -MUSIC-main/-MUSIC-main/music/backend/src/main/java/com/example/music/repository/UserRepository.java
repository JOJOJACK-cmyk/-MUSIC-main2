package com.example.music.repository;

import com.example.music.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    // 💡 닉네임에 유니크 제약이 걸렸으므로 Optional<User>로 단일 조회 가능
    Optional<User> findByNickname(String nickname);

    // 💡 회원가입 시 닉네임 중복 체크를 위한 메서드 추가 (선택 권장)
    boolean existsByNickname(String nickname);
}