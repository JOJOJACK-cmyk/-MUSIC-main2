package com.example.music.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private MockHttpServletResponse post(AuthRateLimitFilter filter, String path, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void 한도를_넘으면_429() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        for (int i = 0; i < 3; i++) {
            assertThat(post(filter, "/api/auth/send-code", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = post(filter, "/api/auth/send-code", "1.1.1.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();

        // 다른 IP 는 영향 없음
        assertThat(post(filter, "/api/auth/send-code", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void 제한_대상이_아닌_경로는_통과() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        for (int i = 0; i < 50; i++) {
            assertThat(post(filter, "/api/auth/me", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }
}
