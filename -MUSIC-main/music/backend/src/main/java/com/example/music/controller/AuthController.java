package com.example.music.controller;

import com.example.music.dto.LoginRequestDto;
import com.example.music.dto.SignupRequestDto;
import com.example.music.entity.User;
import com.example.music.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;


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

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity
                    .internalServerError()
                    .body("회원가입 오류: " + e.getMessage());
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
                            List.of(
                                    new SimpleGrantedAuthority(
                                            user.getRole()
                                    )
                            )
                    );


            // 3. SecurityContext 생성
            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();


            // 4. 인증 정보를 SecurityContext에 저장
            context.setAuthentication(authentication);


            // 5. 현재 요청의 로그인 상태 등록
            SecurityContextHolder.setContext(context);


            // 6. 세션에 SecurityContext 저장
            request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository
                            .SPRING_SECURITY_CONTEXT_KEY,
                    context
            );


            return ResponseEntity.ok("로그인 성공");


        } catch (IllegalArgumentException e) {

            // 이메일 없음 / 비밀번호 불일치 / 소셜 회원 등
            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity
                    .internalServerError()
                    .body("로그인 오류: " + e.getMessage());
        }
    }
}