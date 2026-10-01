package com.example.music.security;

import com.example.music.entity.User;
import com.example.music.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthenticatedUserResolver {

    private final UserRepository userRepository;

    public User resolveRequiredUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        String email = extractEmail(authentication);
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("인증 사용자 이메일을 확인할 수 없습니다.");
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("인증 사용자 정보를 찾을 수 없습니다."));
    }

    /**
     * STOMP 메시지 등에서 받은 Principal 로 사용자를 찾는다. 비로그인/알 수 없으면 empty.
     * (웹소켓 핸드셰이크 때 세션 쿠키 또는 ?token= 으로 인증된 사용자가 Principal 로 붙는다)
     */
    public Optional<User> resolveOptionalUser(Principal principal) {
        if (!(principal instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String email = extractEmail(authentication);
        if (email == null || email.isBlank()) return Optional.empty();
        return userRepository.findByEmail(email);
    }

    /** 채팅/투표에 표시할 이름 — 프론트와 같은 규칙(닉네임 → 이메일 앞부분). */
    public static String displayName(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname();
        String email = user.getEmail();
        if (email != null && email.contains("@")) return email.substring(0, email.indexOf('@'));
        return "사용자";
    }

    public String extractEmail(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oauth2User) {
            String email = extractEmailFromAttributes(oauth2User.getAttributes());
            if (email != null && !email.isBlank()) {
                return email;
            }
        }

        if (principal instanceof String principalText && looksLikeEmail(principalText)) {
            return principalText;
        }

        String authenticationName = authentication.getName();
        if (looksLikeEmail(authenticationName)) {
            return authenticationName;
        }

        return null;
    }

    private String extractEmailFromAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        Object directEmail = attributes.get("email");
        if (directEmail instanceof String email && !email.isBlank()) {
            return email;
        }

        Object kakaoAccount = attributes.get("kakao_account");
        if (kakaoAccount instanceof Map<?, ?> kakaoMap) {
            Object kakaoEmail = kakaoMap.get("email");
            if (kakaoEmail instanceof String email && !email.isBlank()) {
                return email;
            }
        }

        Object naverResponse = attributes.get("response");
        if (naverResponse instanceof Map<?, ?> naverMap) {
            Object naverEmail = naverMap.get("email");
            if (naverEmail instanceof String email && !email.isBlank()) {
                return email;
            }
        }

        return null;
    }

    private boolean looksLikeEmail(String value) {
        return value != null && !value.isBlank() && value.contains("@");
    }
}
