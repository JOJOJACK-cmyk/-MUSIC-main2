package com.example.music.controller;

import com.example.music.dto.LoginRequestDto;
import com.example.music.dto.SignupRequestDto;
import com.example.music.entity.User;
import com.example.music.repository.UserRepository;
import com.example.music.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;

    // 회원가입 API
    // POST /api/auth/signup
    @PostMapping("/signup")
    public ResponseEntity<String> signup(
            @Valid @RequestBody SignupRequestDto requestDto
    ) {
        try {
            userService.signup(requestDto);
            return ResponseEntity.ok("회원가입 성공");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("회원가입 오류: " + e.getMessage());
        }
    }

    // 로그인 API
    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequestDto requestDto,
            HttpServletRequest request
    ) {
        try {
            // 1. 이메일 / 비밀번호 검사
            User user = userService.login(requestDto);

            // 2. Spring Security 인증 객체 생성
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            List.of(new SimpleGrantedAuthority(user.getRole()))
                    );

            // 3. SecurityContext 생성 및 저장
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            // 4. 세션에 SecurityContext 저장
            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context
            );

            // 5. 프론트엔드 헤더 및 상태 관리에 쓸 수 있도록 role을 포함하여 사용자 정보 반환
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("email", user.getEmail());
            userInfo.put("nickname", user.getNickname());
            userInfo.put("profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "");
            userInfo.put("role", user.getRole()); // 💡 결제/권한 확인을 위한 role 추가

            return ResponseEntity.ok(userInfo);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("로그인 오류: " + e.getMessage());
        }
    }

    // 내 정보 조회 API
    // GET /api/auth/me
    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인되어 있지 않습니다.");
        }

        Object principal = authentication.getPrincipal();
        Map<String, Object> response = new HashMap<>();

        // A. OAuth2 소셜 로그인 사용자인 경우
        if (principal instanceof OAuth2User oauth2User) {
            Map<String, Object> attributes = oauth2User.getAttributes();
            String email = "";
            String nickname = "소셜사용자";
            String profileImageUrl = "";

            if (attributes.containsKey("kakao_account")) { // 카카오
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                email = (String) kakaoAccount.get("email");
                if (kakaoAccount.containsKey("profile")) {
                    Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                    nickname = (String) profile.get("nickname");
                    profileImageUrl = (String) profile.get("profile_image_url");
                }
            } else if (attributes.containsKey("response")) { // 네이버
                Map<String, Object> naverResponse = (Map<String, Object>) attributes.get("response");
                email = (String) naverResponse.get("email");
                nickname = (String) naverResponse.get("nickname");
                profileImageUrl = (String) naverResponse.get("profile_image");
            } else { // 구글 등 기본
                email = (String) attributes.get("email");
                nickname = (String) attributes.get("name");
                profileImageUrl = (String) attributes.get("picture");
            }

            // DB에 저장된 유저 정보가 있다면 role을 포함하여 최신화
            if (email != null && !email.isEmpty()) {
                Optional<User> optionalUser = userRepository.findByEmail(email);
                if (optionalUser.isPresent()) {
                    User dbUser = optionalUser.get();
                    response.put("id", dbUser.getId());
                    response.put("email", dbUser.getEmail());
                    response.put("nickname", dbUser.getNickname());
                    response.put("profileImageUrl", dbUser.getProfileImageUrl() != null ? dbUser.getProfileImageUrl() : profileImageUrl);
                    response.put("role", dbUser.getRole()); // 💡 role 추가
                    return ResponseEntity.ok(response);
                }
            }

            response.put("email", email != null ? email : "");
            response.put("nickname", nickname != null ? nickname : "소셜사용자");
            response.put("profileImageUrl", profileImageUrl != null ? profileImageUrl : "");
            response.put("role", "USER");
            return ResponseEntity.ok(response);
        }

        // B. 일반 폼 로그인 사용자인 경우
        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            response.put("id", user.getId());
            response.put("email", user.getEmail());
            response.put("nickname", user.getNickname());
            response.put("profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "");
            response.put("role", user.getRole()); // 💡 role 추가
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유저를 찾을 수 없습니다.");
    }

    // 로그아웃 API
    // POST /api/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("로그아웃 성공");
    }
}