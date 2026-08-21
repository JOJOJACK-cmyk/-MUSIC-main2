package com.example.music.config;

import com.example.music.service.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final ClientRegistrationRepository clientRegistrationRepository; // 💡 추가: ClientRegistrationRepository 주입

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/ws-chat/**"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/musics/**")
                        .permitAll()

                        .requestMatchers(
                                "/api/musics/youtube"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                )

                .oauth2Login(oauth2 -> oauth2
                        // 💡 [핵심 추가] 네이버 로그인 시 무조건 아이디/비밀번호 입력창(reprompt)을 띄우도록 설정
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(
                                        new CustomOAuth2AuthorizationRequestResolver(clientRegistrationRepository)
                                )
                        )
                        .successHandler((request, response, authentication) -> {
                            response.sendRedirect("http://localhost:3000/");
                        })
                        .failureHandler((request, response, exception) -> {
                            exception.printStackTrace();
                            String errorMessage = URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8);
                            response.sendRedirect("http://localhost:3000/login?error=" + errorMessage);
                        })
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(200))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    // 💡 [핵심 클래스 추가] OAuth2 인증 요청 시 auth_type=reprompt 파라미터를 강제로 붙여주는 Resolver
    private static class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
        private final OAuth2AuthorizationRequestResolver defaultResolver;

        public CustomOAuth2AuthorizationRequestResolver(ClientRegistrationRepository repo) {
            this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
            OAuth2AuthorizationRequest req = defaultResolver.resolve(request);
            return customize(req);
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
            OAuth2AuthorizationRequest req = defaultResolver.resolve(request, clientRegistrationId);
            return customize(req);
        }

        private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest req) {
            if (req == null) return null;

            Map<String, Object> extraParams = new HashMap<>(req.getAdditionalParameters());

            // 네이버 OAuth 요청 시 재인증(아이디/비밀번호 다시 입력) 파라미터 추가
            if (req.getAuthorizationUri().contains("naver")) {
                extraParams.put("auth_type", "reprompt");
            }

            return OAuth2AuthorizationRequest.from(req)
                    .additionalParameters(extraParams)
                    .build();
        }
    }

    // SecurityConfig.java 내부에 추가
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}