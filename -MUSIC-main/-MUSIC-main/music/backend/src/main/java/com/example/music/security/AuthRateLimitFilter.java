package com.example.music.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 로그인/회원가입/인증 코드 API 에 IP 기준 요청 수 제한을 건다 (고정 1분 창).
 * 비밀번호 대입, 인증 메일 폭탄, 인증 코드 대입을 막기 위한 것.
 *
 * 단일 서버 배포 기준 인메모리 카운터. (prod 는 server.forward-headers-strategy=framework 라
 * getRemoteAddr() 가 Caddy 뒤의 실제 클라이언트 IP 를 돌려준다)
 */
@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;

    /** 경로 → 1분당 허용 횟수 (POST 만 제한) */
    private static final Map<String, Integer> LIMITS = Map.of(
            "/api/auth/login", 10,
            "/api/auth/signup", 5,
            "/api/auth/send-code", 3,
            "/api/auth/find-email/send-code", 3,
            "/api/auth/verify-and-reset", 10,
            "/api/auth/find-email/verify", 10
    );

    private record Window(long startedAt, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastSweep = System.currentTimeMillis();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !LIMITS.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        int limit = LIMITS.get(path);
        long now = System.currentTimeMillis();
        sweepExpired(now);

        String key = path + "|" + request.getRemoteAddr();
        Window w = windows.compute(key, (k, cur) ->
                (cur == null || now - cur.startedAt() >= WINDOW_MILLIS)
                        ? new Window(now, new AtomicInteger())
                        : cur);

        if (w.count().incrementAndGet() > limit) {
            long retryAfterSec = Math.max(1, (WINDOW_MILLIS - (now - w.startedAt())) / 1000);
            log.warn("[RateLimit] 요청 제한 초과 path={} ip={}", path, request.getRemoteAddr());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSec));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"message\":\"요청이 너무 많습니다. " + retryAfterSec + "초 후 다시 시도해주세요.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /** 1분마다 만료된 창 정리 (메모리 누적 방지) */
    private void sweepExpired(long now) {
        if (now - lastSweep < WINDOW_MILLIS) return;
        lastSweep = now;
        windows.entrySet().removeIf(e -> now - e.getValue().startedAt() >= WINDOW_MILLIS);
    }
}
