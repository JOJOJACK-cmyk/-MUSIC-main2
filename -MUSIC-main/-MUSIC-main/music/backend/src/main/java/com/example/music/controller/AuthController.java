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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender javaMailSender; // 💡 실제 이메일 전송을 위한 MailSender 주입

    // 이메일 인증 코드를 임시 저장할 맵
    private final Map<String, String> verificationCodes = new ConcurrentHashMap<>();

    // 💡 실제 메일 발송을 처리하는 헬퍼 메서드
    private void sendEmail(String toEmail, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(text);
        javaMailSender.send(message);
    }

    // 회원가입 API
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
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequestDto requestDto,
            HttpServletRequest request
    ) {
        try {
            User user = userService.login(requestDto);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            List.of(new SimpleGrantedAuthority(user.getRole()))
                    );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context
            );

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("email", user.getEmail());
            userInfo.put("nickname", user.getNickname());
            userInfo.put("profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "");
            userInfo.put("role", user.getRole());

            return ResponseEntity.ok(userInfo);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("로그인 오류: " + e.getMessage());
        }
    }

    // ==========================================
    // 🔒 [보안 강화] 아이디 찾기 2단계 인증 프로세스 (실제 메일 전송)
    // ==========================================

    @PostMapping("/find-email/send-code")
    public ResponseEntity<?> sendEmailFindCode(@RequestBody Map<String, String> request) {
        String nickname = request.get("nickname");

        if (nickname == null || nickname.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "닉네임을 입력해주세요."));
        }

        Optional<User> optionalUser = userRepository.findByNickname(nickname.trim());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "해당 닉네임으로 가입된 사용자가 없습니다."));
        }

        User user = optionalUser.get();
        if (user.getProvider() != null && !user.getProvider().equals("local")) {
            return ResponseEntity.badRequest().body(Map.of("message", "소셜 로그인 계정입니다. 해당 소셜 로그인을 이용해주세요."));
        }

        String code = String.format("%06d", new Random().nextInt(1000000));
        verificationCodes.put(user.getEmail(), code);

        // 💡 실제 사용자의 이메일함으로 인증 번호 전송
        try {
            sendEmail(
                    user.getEmail(),
                    "[Music 서비스] 아이디 찾기 인증 코드",
                    "안녕하세요, Music 서비스입니다.\n요청하신 아이디 찾기 인증 코드는 [" + code + "] 입니다."
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "이메일 전송에 실패했습니다. 관리자에 문의해주세요."));
        }

        return ResponseEntity.ok(Map.of(
                "message", "인증 코드가 이메일로 전송되었습니다.",
                "emailHint", user.getEmail().replaceAll("(?<=.{3}).(?=.*@)", "*")
        ));
    }

    @PostMapping("/find-email/verify")
    public ResponseEntity<?> verifyAndFindEmail(@RequestBody Map<String, String> request) {
        String nickname = request.get("nickname");
        String code = request.get("code");

        if (nickname == null || code == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "닉네임과 인증 코드를 모두 입력해주세요."));
        }

        Optional<User> optionalUser = userRepository.findByNickname(nickname.trim());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "가입된 사용자를 찾을 수 없습니다."));
        }

        User user = optionalUser.get();
        String savedCode = verificationCodes.get(user.getEmail());

        if (savedCode == null || !savedCode.equals(code.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "인증 코드가 일치하지 않습니다."));
        }

        verificationCodes.remove(user.getEmail());

        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "message", "인증이 완료되었습니다."
        ));
    }

    // ==========================================
    // 🔒 [보안 강화] 비밀번호 찾기 2단계 인증 프로세스 (실제 메일 전송)
    // ==========================================

    @PostMapping("/send-code")
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "이메일을 입력해주세요."));
        }

        Optional<User> optionalUser = userRepository.findByEmail(email.trim());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "가입되지 않은 이메일입니다."));
        }

        User user = optionalUser.get();
        if (user.getProvider() != null && !user.getProvider().equals("local")) {
            return ResponseEntity.badRequest().body(Map.of("message", "소셜 로그인 계정은 비밀번호를 찾을 수 없습니다."));
        }

        String code = String.format("%06d", new Random().nextInt(1000000));
        verificationCodes.put(email.trim(), code);

        // 💡 실제 사용자의 이메일함으로 인증 번호 전송
        try {
            sendEmail(
                    email.trim(),
                    "[Music 서비스] 비밀번호 찾기 인증 코드",
                    "안녕하세요, Music 서비스입니다.\n요청하신 비밀번호 찾기 인증 코드는 [" + code + "] 입니다."
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "이메일 전송에 실패했습니다. 관리자에 문의해주세요."));
        }

        return ResponseEntity.ok(Map.of("message", "인증 코드가 이메일로 전송되었습니다."));
    }

    @PostMapping("/verify-and-reset")
    public ResponseEntity<?> verifyAndReset(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");

        if (email == null || code == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "이메일과 인증 코드를 모두 입력해주세요."));
        }

        String savedCode = verificationCodes.get(email.trim());
        if (savedCode == null || !savedCode.equals(code.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "인증 코드가 일치하지 않습니다."));
        }

        verificationCodes.remove(email.trim());

        Optional<User> optionalUser = userRepository.findByEmail(email.trim());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "가입되지 않은 이메일입니다."));
        }

        User user = optionalUser.get();

        String tempPassword = UUID.randomUUID().toString().substring(0, 8);
        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "tempPassword", tempPassword,
                "message", "인증이 완료되었습니다. 임시 비밀번호가 발급되었습니다."
        ));
    }

    // 내 정보 조회 API
    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인되어 있지 않습니다.");
        }

        Object principal = authentication.getPrincipal();
        Map<String, Object> response = new HashMap<>();

        if (principal instanceof OAuth2User oauth2User) {
            Map<String, Object> attributes = oauth2User.getAttributes();
            String email = "";
            String nickname = "소셜사용자";
            String profileImageUrl = "";

            if (attributes.containsKey("kakao_account")) {
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                email = (String) kakaoAccount.get("email");
                if (kakaoAccount.containsKey("profile")) {
                    Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                    nickname = (String) profile.get("nickname");
                    profileImageUrl = (String) profile.get("profile_image_url");
                }
            } else if (attributes.containsKey("response")) {
                Map<String, Object> naverResponse = (Map<String, Object>) attributes.get("response");
                email = (String) naverResponse.get("email");
                nickname = (String) naverResponse.get("nickname");
                profileImageUrl = (String) naverResponse.get("profile_image");
            } else {
                email = (String) attributes.get("email");
                nickname = (String) attributes.get("name");
                profileImageUrl = (String) attributes.get("picture");
            }

            if (email != null && !email.isEmpty()) {
                Optional<User> optionalUser = userRepository.findByEmail(email);
                if (optionalUser.isPresent()) {
                    User dbUser = optionalUser.get();
                    response.put("id", dbUser.getId());
                    response.put("email", dbUser.getEmail());
                    response.put("nickname", dbUser.getNickname());
                    response.put("profileImageUrl", dbUser.getProfileImageUrl() != null ? dbUser.getProfileImageUrl() : profileImageUrl);
                    response.put("role", dbUser.getRole());
                    return ResponseEntity.ok(response);
                }
            }

            response.put("email", email != null ? email : "");
            response.put("nickname", nickname != null ? nickname : "소셜사용자");
            response.put("profileImageUrl", profileImageUrl != null ? profileImageUrl : "");
            response.put("role", "USER");
            return ResponseEntity.ok(response);
        }

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            response.put("id", user.getId());
            response.put("email", user.getEmail());
            response.put("nickname", user.getNickname());
            response.put("profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "");
            response.put("role", user.getRole());
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유저를 찾을 수 없습니다.");
    }

    // 로그아웃 API
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