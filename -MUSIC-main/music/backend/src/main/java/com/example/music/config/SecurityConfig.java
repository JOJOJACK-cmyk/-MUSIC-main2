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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/ws-chat/**",
                                "/ws/**",
                                "/api/v1/logs/**",
                                "/api/logs/**"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/musics/**", "/api/broadcast/**")
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
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(
                                        new CustomOAuth2AuthorizationRequestResolver(clientRegistrationRepository)
                                )
                        )
                        .successHandler((request, response, authentication) -> {
                            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
                            String registrationId = oauthToken.getAuthorizedClientRegistrationId().toLowerCase();
                            OAuth2User oAuth2User = oauthToken.getPrincipal();
                            Map<String, Object> attributes = oAuth2User.getAttributes();

                            String nickname = "사용자";
                            String email = "";
                            String profileImageUrl = "";

                            if ("kakao".equals(registrationId)) {
                                if (attributes.containsKey("kakao_account")) {
                                    Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                                    if (kakaoAccount != null) {
                                        email = (String) kakaoAccount.get("email");
                                        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                                        if (profile != null) {
                                            nickname = (String) profile.get("nickname");
                                            profileImageUrl = (String) profile.get("profile_image_url");
                                        }
                                    }
                                } else {
                                    nickname = (String) attributes.getOrDefault("nickname", attributes.get("name"));
                                    email = (String) attributes.get("email");
                                    profileImageUrl = (String) attributes.getOrDefault("profileImageUrl", attributes.get("profile_image_url"));
                                }
                            } else if ("naver".equals(registrationId)) {
                                if (attributes.containsKey("response")) {
                                    Map<String, Object> naverResp = (Map<String, Object>) attributes.get("response");
                                    if (naverResp != null) {
                                        email = (String) naverResp.get("email");
                                        nickname = (String) naverResp.get("nickname");
                                        if (nickname == null || nickname.isEmpty()) {
                                            nickname = (String) naverResp.get("name");
                                        }
                                        profileImageUrl = (String) naverResp.get("profile_image");
                                    }
                                } else {
                                    nickname = (String) attributes.getOrDefault("nickname", attributes.get("name"));
                                    email = (String) attributes.get("email");
                                    profileImageUrl = (String) attributes.getOrDefault("profileImageUrl", attributes.get("profile_image"));
                                }
                            } else if ("google".equals(registrationId)) {
                                nickname = (String) attributes.get("name");
                                email = (String) attributes.get("email");
                                profileImageUrl = (String) attributes.get("picture");
                            }

                            if (nickname == null || nickname.isEmpty()) {
                                nickname = (String) attributes.getOrDefault("nickname", attributes.getOrDefault("name", "소셜사용자"));
                            }
                            if (email == null) email = "";
                            if (profileImageUrl == null) profileImageUrl = "";

                            String encodedNickname = URLEncoder.encode(nickname, StandardCharsets.UTF_8);
                            String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
                            String encodedProfile = URLEncoder.encode(profileImageUrl, StandardCharsets.UTF_8);

                            response.sendRedirect(String.format(
                                    "http://localhost:3000/?nickname=%s&email=%s&profileImageUrl=%s",
                                    encodedNickname, encodedEmail, encodedProfile
                            ));
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 💡 null(로컬 HTML 직접 열기) 및 localhost 모든 포트 허용
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "http://localhost:8080",
                "null",
                "*"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

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

            if (req.getAuthorizationUri() != null && req.getAuthorizationUri().contains("naver")) {
                extraParams.put("auth_type", "reprompt");
            }

            return OAuth2AuthorizationRequest.from(req)
                    .additionalParameters(extraParams)
                    .build();
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}