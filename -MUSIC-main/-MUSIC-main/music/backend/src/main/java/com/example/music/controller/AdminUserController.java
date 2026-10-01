package com.example.music.controller;

import com.example.music.entity.User;
import com.example.music.repository.UserRepository;
import com.example.music.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 회원 권한(역할) 관리 API. 최고 관리자(ROLE_ADMIN)만 접근 가능
 * (SecurityConfig 에서 /api/admin/** → hasRole("ADMIN") 로 제한).
 */
@Tag(name = "Admin User API", description = "회원 권한 부여/관리 (최고 관리자 전용)")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    // 부여 가능한 권한
    private static final Set<String> ASSIGNABLE_ROLES =
            Set.of("ROLE_USER", "ROLE_SUB_ADMIN", "ROLE_ADMIN");

    private Map<String, Object> toDto(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("nickname", u.getNickname());
        m.put("role", u.getRole());
        m.put("profileImageUrl", u.getProfileImageUrl());
        return m;
    }

    @Operation(summary = "회원 목록/검색", description = "q 가 있으면 이메일·닉네임으로 검색, 없으면 최근 가입 50명")
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String q) {
        List<User> users = (q == null || q.isBlank())
                ? userRepository.findTop50ByOrderByIdDesc()
                : userRepository.searchByEmailOrNickname(q.trim());
        return users.stream().map(this::toDto).toList();
    }

    @Operation(summary = "회원 권한 변경",
            description = "role 은 ROLE_USER / ROLE_SUB_ADMIN / ROLE_ADMIN 중 하나. 본인 권한은 변경 불가.")
    @PatchMapping("/{id}/role")
    @Transactional
    public ResponseEntity<?> setRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        User me = authenticatedUserResolver.resolveRequiredUser(authentication);
        if (me.getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "본인 권한은 변경할 수 없습니다."));
        }

        String role = body.get("role");
        if (role == null || !ASSIGNABLE_ROLES.contains(role)) {
            return ResponseEntity.badRequest().body(Map.of("message", "허용되지 않은 권한 값입니다."));
        }

        User target = userRepository.findById(id).orElse(null);
        if (target == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "사용자를 찾을 수 없습니다."));
        }

        target.setRole(role);
        userRepository.save(target);
        return ResponseEntity.ok(toDto(target));
    }
}
