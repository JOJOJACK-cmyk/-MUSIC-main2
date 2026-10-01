package com.example.music.service;

import com.example.music.entity.User;
import com.example.music.repository.UserRepository;
import com.example.music.security.OAuthAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final int NICKNAME_MAX = 20;

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        OAuthAttributes attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oAuth2User.getAttributes());

        // 이메일로 계정을 찾으므로, 제공자가 "인증된 이메일"이라고 보증한 경우에만 로그인시킨다.
        // (미인증 이메일을 허용하면 남의 이메일을 넣은 소셜 계정으로 그 사람의 계정에 들어갈 수 있다)
        if (attributes.getEmail() == null || attributes.getEmail().isBlank()) {
            throw oauthError("email_required", "이메일 제공에 동의해야 로그인할 수 있습니다.");
        }
        if (!attributes.isEmailVerified()) {
            throw oauthError("email_not_verified", "소셜 계정의 이메일 인증이 완료되지 않았습니다. 이메일 인증 후 다시 시도해 주세요.");
        }

        User user = saveOrUpdate(registrationId, attributes);

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(user.getRole())),
                attributes.getAttributes(),
                attributes.getNameAttributeKey()
        );
    }

    // 패키지 공개: 테스트에서 계정 연결 규칙을 직접 검증
    User saveOrUpdate(String registrationId, OAuthAttributes attributes) {
        User user = userRepository.findByEmail(attributes.getEmail())
                .map(existing -> {
                    // 같은 이메일의 일반(비밀번호) 계정에 소셜로 처음 연결되는 경우:
                    // 이메일 인증 없이 가입된 계정일 수 있으므로(남이 먼저 내 이메일로 가입해 둔 경우)
                    // 기존 비밀번호를 무효화하고 소셜 계정으로 전환한다. 인증된 이메일의 주인만 계속 쓸 수 있다.
                    boolean isLocal = existing.getProvider() == null || "local".equals(existing.getProvider());
                    if (isLocal) {
                        if (existing.getPassword() != null) {
                            log.info("[OAuth] 일반 계정을 소셜({})로 전환 - 기존 비밀번호 무효화 userId={}",
                                    registrationId, existing.getId());
                        }
                        existing.setPassword(null);
                        existing.setProvider(registrationId);
                        existing.setProviderId(attributes.getProviderId());
                    }
                    // 닉네임은 사용자가 바꿨을 수 있고 유니크라 덮어쓰지 않는다. 프로필 사진만 비어 있으면 채운다.
                    if (existing.getProfileImageUrl() == null || existing.getProfileImageUrl().isBlank()) {
                        existing.setProfileImageUrl(attributes.getProfileImageUrl());
                    }
                    return existing;
                })
                .orElseGet(() -> User.builder()
                        .email(attributes.getEmail())
                        .nickname(uniqueNickname(attributes.getNickname()))
                        .profileImageUrl(attributes.getProfileImageUrl())
                        .provider(registrationId)
                        .providerId(attributes.getProviderId())
                        .role("ROLE_USER")
                        .build());

        return userRepository.save(user);
    }

    /** 소셜 닉네임은 다른 회원과 겹칠 수 있다(닉네임은 유니크) → 겹치면 숫자를 붙인다 */
    private String uniqueNickname(String raw) {
        String base = raw == null ? "" : raw.trim();
        if (base.isEmpty() || "게스트".equals(base)) base = "사용자";
        if (base.length() > NICKNAME_MAX - 5) base = base.substring(0, NICKNAME_MAX - 5);
        if (!userRepository.existsByNickname(base)) return base;
        for (int i = 0; i < 20; i++) {
            String candidate = base + (1000 + (int) (Math.random() * 9000));
            if (!userRepository.existsByNickname(candidate)) return candidate;
        }
        return base + UUID.randomUUID().toString().substring(0, 4);
    }

    private static OAuth2AuthenticationException oauthError(String code, String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, message, null), message);
    }
}
