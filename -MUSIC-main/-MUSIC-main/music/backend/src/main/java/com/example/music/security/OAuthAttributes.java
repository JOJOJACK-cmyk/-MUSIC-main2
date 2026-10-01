package com.example.music.security;

import com.example.music.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
public class OAuthAttributes {
    private Map<String, Object> attributes;
    private String nameAttributeKey;
    private String nickname;
    private String email;
    private String profileImageUrl;
    private String providerId;
    // 제공자가 이메일 소유를 인증했는지. false 면 이메일로 기존 계정에 연결하면 안 된다.
    private boolean emailVerified;

    @Builder
    public OAuthAttributes(Map<String, Object> attributes, String nameAttributeKey, String nickname, String email,
                           String profileImageUrl, String providerId, boolean emailVerified) {
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
        this.nickname = nickname;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.providerId = providerId;
        this.emailVerified = emailVerified;
    }

    public static OAuthAttributes of(String registrationId, String userNameAttributeName, Map<String, Object> attributes) {
        if ("naver".equals(registrationId)) {
            return ofNaver("id", attributes);
        } else if ("kakao".equals(registrationId)) {
            return ofKakao(userNameAttributeName, attributes);
        }
        return ofGoogle(userNameAttributeName, attributes);
    }

    // Google 파싱 — email_verified 가 true 인 경우만 인증된 이메일
    private static OAuthAttributes ofGoogle(String userNameAttributeName, Map<String, Object> attributes) {
        return OAuthAttributes.builder()
                .nickname((String) attributes.get("name"))
                .email((String) attributes.get("email"))
                .profileImageUrl((String) attributes.get("picture"))
                .providerId(str(attributes.get("sub")))
                .emailVerified(Boolean.TRUE.equals(attributes.get("email_verified")))
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    // Kakao 파싱 — 카카오 계정 이메일은 미인증일 수 있으므로 is_email_valid && is_email_verified 를 확인
    @SuppressWarnings("unchecked")
    private static OAuthAttributes ofKakao(String userNameAttributeName, Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = attributes.get("kakao_account") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        Map<String, Object> profile = kakaoAccount.get("profile") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();

        boolean verified = Boolean.TRUE.equals(kakaoAccount.get("is_email_valid"))
                && Boolean.TRUE.equals(kakaoAccount.get("is_email_verified"));

        return OAuthAttributes.builder()
                .nickname((String) profile.get("nickname"))
                .email((String) kakaoAccount.get("email"))
                .profileImageUrl((String) profile.get("profile_image_url"))
                .providerId(str(attributes.get("id")))
                .emailVerified(verified)
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    // Naver 파싱 — 네이버가 제공하는 이메일은 네이버가 확인한 연락처 이메일
    @SuppressWarnings("unchecked")
    private static OAuthAttributes ofNaver(String userNameAttributeName, Map<String, Object> attributes) {
        Map<String, Object> response = attributes.get("response") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        return OAuthAttributes.builder()
                .nickname((String) response.get("name"))
                .email((String) response.get("email"))
                .profileImageUrl((String) response.get("profile_image"))
                .providerId(str(response.get("id")))
                .emailVerified(response.get("email") != null)
                .attributes(response)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public User toEntity() {
        return User.builder()
                .nickname(nickname)
                .email(email)
                .profileImageUrl(profileImageUrl)
                .providerId(providerId)
                .role("ROLE_USER") // 신규 가입 시 기본 권한
                .build();
    }
}
