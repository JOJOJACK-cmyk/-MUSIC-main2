package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    // 소셜 로그인 사용자는 비밀번호가 없을 수 있으므로 nullable = true
    @Column(nullable = true)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String role; // "ROLE_USER", "ROLE_ADMIN" 등

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(length = 20)
    private String provider; // "google", "kakao" 등

    @Column(name = "provider_id")
    private String providerId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public User(String email, String password, String nickname, String role, String profileImageUrl, String provider, String providerId) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role != null ? role : "ROLE_USER";
        this.profileImageUrl = profileImageUrl;
        this.provider = provider;
        this.providerId = providerId;
    }

    // 소셜 프로필 업데이트용 메서드
    public User updateOAuthProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        return this;
    }
}