package com.example.music.config;

import com.example.music.entity.User;
import com.example.music.repository.UserRepository;
import com.example.music.security.CustomAccessDeniedHandler;
import com.example.music.security.CustomAuthenticationEntryPoint;
import com.example.music.security.TokenAuthFilter;
import com.example.music.service.CustomOAuth2UserService;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize 등 메서드 단위 인가 제어를 위해 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final UserRepository userRepository; // 💡 DB에서 유저 정보를 조회하기 위해 추가

    // 인증/인가 예외 핸들러 주입
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    // Bearer 토큰 인증 필터 (SPA <-> API stateless 인증)
    private final TokenAuthFilter tokenAuthFilter;
    private final com.example.music.security.AuthTokenService authTokenService;

    // 환경별로 다른 프론트 주소 (dev: localhost:3000, prod: 실제 도메인)
    @Value("${app.frontend-url}")
    private String frontendUrl;

    // 콤마로 여러 개 지정 가능
    @Value("${app.cors.allowed-origins}")
    private String allowedOriginsRaw;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        // 1. 인증, OAuth, 문서, 웹소켓 등 기본 퍼블릭 경로 허용
                        .requestMatchers(
                                "/api/auth/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/ws-chat/**",
                                "/ws-stomp/**",
                                "/ws/**"
                        ).permitAll()

                        // 2. 결제 API는 인증된 유저만 접근 가능
                        .requestMatchers("/api/v1/payments/**", "/api/payments/**").authenticated()

                        // 3. 청취 로그 및 권한 검증 관련 API 인증 설정
                        .requestMatchers("/api/v1/logs/**", "/api/logs/**").authenticated()

                        // ==========================================
                        // 관리자(ADMIN) 권한 전용 API 경로 설정
                        // ==========================================
                        // 회원 권한 부여/관리 - 최고 관리자(ROLE_ADMIN)만
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 음원 등록/수정/삭제 - 관리자 + 부 관리자만
                        //  (단, POST /api/musics/{id}/like 는 일반 회원 동작이므로 제외)
                        .requestMatchers("/api/musics/register", "/api/musics/admin/**").hasAnyRole("ADMIN", "SUB_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/musics", "/api/musics/youtube", "/api/musics/youtube/**")
                        .hasAnyRole("ADMIN", "SUB_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/musics/**").hasAnyRole("ADMIN", "SUB_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/musics/**").hasAnyRole("ADMIN", "SUB_ADMIN")

                        // S3 직접 업로드 - 관리자 + 부 관리자 (일반 회원이 버킷에 파일을 올리지 못하게)
                        .requestMatchers("/api/s3/**").hasAnyRole("ADMIN", "SUB_ADMIN")

                        // 스토어 관리 - 관리자 + 부 관리자 (아래 상품 조회 permitAll 보다 먼저)
                        .requestMatchers("/api/shop/admin/**").hasAnyRole("ADMIN", "SUB_ADMIN")
                        // 스토어 상품 조회는 누구나 (주문/결제는 아래 anyRequest().authenticated())
                        .requestMatchers(HttpMethod.GET, "/api/shop/products", "/api/shop/products/**").permitAll()

                        // 4. 기존 음악 조회 등 퍼블릭 경로 (차트/알림 조회 포함)
                        .requestMatchers(HttpMethod.GET, "/api/musics/**", "/api/broadcast/**", "/api/music-snapshot/**",
                                "/api/live/status", "/api/chart/**", "/api/notifications/**")
                        .permitAll()

                        // 시청자 heartbeat는 비로그인 사용자의 시청자 수 집계를 위해 메서드 제한 없이 공개
                        .requestMatchers("/api/broadcast/*/viewers/heartbeat").permitAll()

                        // SRS 웹훅 콜백 - 로그인 사용자가 아니라 SRS 서버가 직접 호출하는 경로
                        .requestMatchers("/api/broadcast/srs/**").permitAll()

                        // 5. 그 외 모든 요청은 로그인(인증)된 사용자만 접근 가능
                        .anyRequest().authenticated()
                )

                // 401(인증 실패), 403(권한 부족) 커스텀 핸들러 연결
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
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

                            // 💡 1. DB에서 해당 이메일 유저의 최신 권한(role) 조회
                            String role = "ROLE_USER";
                            String token = "";
                            if (!email.isEmpty()) {
                                User dbUser = userRepository.findByEmail(email).orElse(null);
                                if (dbUser != null) {
                                    if (dbUser.getRole() != null) role = dbUser.getRole();
                                    // 💡 SPA 에서 쓸 액세스 토큰 발급 (cross-origin 세션 문제 회피)
                                    token = authTokenService.issue(dbUser.getId());
                                }
                            }

                            String encodedNickname = URLEncoder.encode(nickname, StandardCharsets.UTF_8);
                            String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
                            String encodedProfile = URLEncoder.encode(profileImageUrl, StandardCharsets.UTF_8);
                            String encodedRole = URLEncoder.encode(role, StandardCharsets.UTF_8);
                            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);

                            // 💡 토큰은 쿼리(?)가 아니라 프래그먼트(#)로 전달한다 — 프래그먼트는 서버 로그·Referer 로
                            //    나가지 않는다. 프론트(AuthContext)가 읽은 뒤 주소창에서 지운다.
                            //    role 은 화면 힌트일 뿐이며 프론트는 /api/auth/me 응답으로 다시 확인한다.
                            response.sendRedirect(String.format(
                                    "%s/#nickname=%s&email=%s&profileImageUrl=%s&role=%s&token=%s",
                                    frontendUrl, encodedNickname, encodedEmail, encodedProfile, encodedRole, encodedToken
                            ));
                        })
                        .failureHandler((request, response, exception) -> {
                            exception.printStackTrace();
                            String rawMessage = exception.getMessage() != null ? exception.getMessage() : "소셜 로그인에 실패했습니다.";
                            String errorMessage = URLEncoder.encode(rawMessage, StandardCharsets.UTF_8);
                            response.sendRedirect(frontendUrl + "/login?error=" + errorMessage);
                        })
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        // LogoutFilter 가 이 경로를 먼저 처리하므로 Bearer 토큰 폐기도 여기서 한다.
                        // (안 하면 로그아웃 후에도 토큰이 7일간 유효)
                        .addLogoutHandler((request, response, authentication) ->
                                authTokenService.revoke(TokenAuthFilter.extractToken(request)))
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

        configuration.setAllowedOriginPatterns(
                Arrays.stream(allowedOriginsRaw.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList()
        );
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
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