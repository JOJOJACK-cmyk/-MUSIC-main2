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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final com.example.music.repository.PassRepository passRepository;
    private final com.example.music.security.AuthTokenService authTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender javaMailSender; // 💡 실제 이메일 전송을 위한 MailSender 주입

    private boolean hasActivePass(Long userId) {
        if (userId == null) return false;
        return passRepository.existsByUser_IdAndIsActiveTrueAndExpireDateAfter(
                userId, java.time.LocalDateTime.now());
    }

    /** 소셜 로그인 계정인지 (비밀번호가 없거나 provider 가 local 이 아님) — 비밀번호 찾기/변경 대상 아님 */
    private static boolean isSocialAccount(User user) {
        return user.getPassword() == null
                || (user.getProvider() != null && !"local".equals(user.getProvider()));
    }

    // 이메일 인증 코드 임시 저장소 (목적별 키: "find-email:<email>", "reset:<email>")
    //  - 5분 후 만료, 5회 틀리면 폐기, 재발송은 60초 간격 → 6자리 코드 무차별 대입 방지
    private static final long CODE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long RESEND_COOLDOWN_MILLIS = 60 * 1000L;
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final SecureRandom CODE_RANDOM = new SecureRandom();

    private static final class PendingCode {
        final String code;
        final long issuedAt;
        final long expiresAt;
        int failedAttempts;

        PendingCode(String code, long now) {
            this.code = code;
            this.issuedAt = now;
            this.expiresAt = now + CODE_TTL_MILLIS;
        }
    }

    private final Map<String, PendingCode> verificationCodes = new ConcurrentHashMap<>();

    /** 재발송 쿨다운 중이면 true */
    private boolean inResendCooldown(String key) {
        PendingCode prev = verificationCodes.get(key);
        return prev != null && System.currentTimeMillis() - prev.issuedAt < RESEND_COOLDOWN_MILLIS;
    }

    private String issueCode(String key) {
        long now = System.currentTimeMillis();
        verificationCodes.values().removeIf(c -> now > c.expiresAt); // 만료된 코드 청소
        String code = String.format("%06d", CODE_RANDOM.nextInt(1_000_000));
        verificationCodes.put(key, new PendingCode(code, now));
        return code;
    }

    /** 코드 검증. 성공하면 코드를 소모(삭제)한다. 만료/시도 초과 시에도 삭제. */
    private boolean consumeCode(String key, String input) {
        PendingCode pending = verificationCodes.get(key);
        if (pending == null || input == null) return false;
        if (System.currentTimeMillis() > pending.expiresAt) {
            verificationCodes.remove(key);
            return false;
        }
        boolean match = MessageDigest.isEqual(
                pending.code.getBytes(StandardCharsets.UTF_8),
                input.trim().getBytes(StandardCharsets.UTF_8));
        if (match) {
            verificationCodes.remove(key);
            return true;
        }
        synchronized (pending) {
            pending.failedAttempts++;
            if (pending.failedAttempts >= MAX_CODE_ATTEMPTS) verificationCodes.remove(key);
        }
        return false;
    }

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
            org.slf4j.LoggerFactory.getLogger(AuthController.class).error("[회원가입] 처리 실패", e);
            return ResponseEntity.internalServerError().body("회원가입 처리 중 서버 오류가 발생했습니다.");
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
            userInfo.put("premium", hasActivePass(user.getId()));
            // 💡 SPA 에서 Authorization: Bearer 로 쓸 액세스 토큰
            userInfo.put("token", authTokenService.issue(user.getId()));

            return ResponseEntity.ok(userInfo);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuthController.class).error("[로그인] 처리 실패", e);
            return ResponseEntity.internalServerError().body("로그인 처리 중 서버 오류가 발생했습니다.");
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
        if (isSocialAccount(user)) {
            return ResponseEntity.badRequest().body(Map.of("message", "소셜 로그인 계정입니다. 해당 소셜 로그인을 이용해주세요."));
        }

        String codeKey = "find-email:" + user.getEmail();
        if (inResendCooldown(codeKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "잠시 후 다시 시도해주세요. (1분에 한 번 발송할 수 있습니다)"));
        }
        String code = issueCode(codeKey);

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
        if (!consumeCode("find-email:" + user.getEmail(), code)) {
            return ResponseEntity.badRequest().body(Map.of("message", "인증 코드가 일치하지 않거나 만료되었습니다."));
        }

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
        if (isSocialAccount(user)) {
            return ResponseEntity.badRequest().body(Map.of("message", "소셜 로그인 계정은 비밀번호를 찾을 수 없습니다."));
        }

        String codeKey = "reset:" + email.trim();
        if (inResendCooldown(codeKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "잠시 후 다시 시도해주세요. (1분에 한 번 발송할 수 있습니다)"));
        }
        String code = issueCode(codeKey);

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

        if (!consumeCode("reset:" + email.trim(), code)) {
            return ResponseEntity.badRequest().body(Map.of("message", "인증 코드가 일치하지 않거나 만료되었습니다."));
        }

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
                    response.put("premium", hasActivePass(dbUser.getId()));
                    return ResponseEntity.ok(response);
                }
            }

            response.put("email", email != null ? email : "");
            response.put("nickname", nickname != null ? nickname : "소셜사용자");
            response.put("profileImageUrl", profileImageUrl != null ? profileImageUrl : "");
            response.put("role", "ROLE_USER");
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
            response.put("premium", hasActivePass(user.getId()));
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유저를 찾을 수 없습니다.");
    }

    // 프로필(닉네임/프로필사진) 수정 API
    @PatchMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유저를 찾을 수 없습니다.");
        }

        User user = optionalUser.get();
        String nickname = request.get("nickname");
        String profileImageUrl = request.get("profileImageUrl");

        if (nickname != null && !nickname.isBlank()) {
            String trimmed;
            try {
                trimmed = UserService.validateNickname(nickname);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
            if (!trimmed.equals(user.getNickname())
                    && userRepository.findByNickname(trimmed).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "이미 사용 중인 닉네임입니다."));
            }
            user.setNickname(trimmed);
        }
        if (profileImageUrl != null) {
            user.setProfileImageUrl(profileImageUrl.isBlank() ? null : profileImageUrl.trim());
        }
        userRepository.save(user);

        Map<String, Object> body = new HashMap<>();
        body.put("id", user.getId());
        body.put("email", user.getEmail());
        body.put("nickname", user.getNickname());
        body.put("profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "");
        body.put("role", user.getRole());
        body.put("premium", hasActivePass(user.getId()));
        return ResponseEntity.ok(body);
    }

    // 비밀번호 변경 API (로그인 상태 - 프로필 화면에서 현재 비밀번호 확인 후 변경)
    @PatchMapping("/password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "유저를 찾을 수 없습니다."));
        }
        User user = optionalUser.get();

        if (isSocialAccount(user)) {
            return ResponseEntity.badRequest().body(Map.of("message", "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."));
        }

        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");

        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "현재 비밀번호와 새 비밀번호를 모두 입력해주세요."));
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "현재 비밀번호가 일치하지 않습니다."));
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "새 비밀번호는 6자 이상이어야 합니다."));
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "현재 비밀번호와 다른 비밀번호를 입력해주세요."));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
    }

    // 로그아웃(POST /api/auth/logout)은 SecurityConfig 의 LogoutFilter 가 처리한다
    // (세션 무효화 + JSESSIONID 삭제 + Bearer 토큰 폐기). 컨트롤러까지 요청이 오지 않는다.
}