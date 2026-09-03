package com.example.music.security;

import com.example.music.entity.User;
import com.example.music.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer <token> (또는 웹소켓 핸드셰이크의 ?token=) 를 읽어
 * AuthTokenService 로 사용자 확인 후 SecurityContext 에 인증을 세팅한다.
 * 기존 세션 인증과 병행 동작 (세션이 이미 있으면 건드리지 않음).
 */
@Component
@RequiredArgsConstructor
public class TokenAuthFilter extends OncePerRequestFilter {

    private final AuthTokenService authTokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null
                || !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
                || "anonymousUser".equals(SecurityContextHolder.getContext().getAuthentication().getPrincipal())) {

            String token = extractToken(request);
            if (token != null) {
                authTokenService.resolve(token).ifPresent(userId -> {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                user.getEmail(), null,
                                List.of(new SimpleGrantedAuthority(
                                        user.getRole() != null ? user.getRole() : "ROLE_USER")));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                });
            }
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        // SockJS/STOMP 핸드셰이크는 헤더를 못 실으므로 쿼리 파라미터 허용
        String q = request.getParameter("token");
        return (q != null && !q.isBlank()) ? q : null;
    }
}
