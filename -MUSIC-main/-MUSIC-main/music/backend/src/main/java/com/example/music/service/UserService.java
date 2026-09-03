package com.example.music.service;

import com.example.music.dto.LoginRequestDto;
import com.example.music.dto.SignupRequestDto;
import com.example.music.entity.User;
import com.example.music.repository.PassRepository;
import com.example.music.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PassRepository passRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원가입
    @Transactional
    public void signup(SignupRequestDto requestDto) {

        // 1. 이메일 중복 검사
        if (userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 💡 2. 닉네임 중복 검사 추가
        if (userRepository.existsByNickname(requestDto.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        // 3. 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(requestDto.getPassword());

        // 4. User 엔티티 생성
        User user = User.builder()
                .email(requestDto.getEmail())
                .password(encodedPassword)
                .nickname(requestDto.getNickname())
                .provider("local")
                .role("ROLE_USER")
                .build();

        // 5. DB 저장
        userRepository.save(user);
    }

    // 일반 로그인
    @Transactional(readOnly = true)
    public User login(LoginRequestDto requestDto) {

        // 1. 이메일로 회원 찾기
        User user = userRepository.findByEmail(requestDto.getEmail())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "이메일 또는 비밀번호가 올바르지 않습니다."
                        )
                );

        // 2. 소셜 로그인 회원인지 확인
        if (user.getPassword() == null) {
            throw new IllegalArgumentException(
                    "소셜 로그인으로 가입한 회원입니다."
            );
        }

        // 3. 비밀번호 비교
        if (!passwordEncoder.matches(
                requestDto.getPassword(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "이메일 또는 비밀번호가 올바르지 않습니다."
            );
        }

        // 💡 4. 활성화되어 있고 만료일이 지나지 않은 이용권(Pass)이 있는지 확인
        boolean hasActivePass = passRepository.existsByUser_IdAndIsActiveTrueAndExpireDateAfter(
                user.getId(),
                LocalDateTime.now()
        );

        // 5. 결제(이용권) 상태에 따라 동적으로 role 부여
        if (hasActivePass) {
            user.setRole("PREMIUM");
        } else {
            // 이용권이 없으면 기본 유저 권한 유지 (필요에 따라 "USER" 또는 기존 값 처리)
            if (user.getRole() == null || user.getRole().equals("ROLE_USER")) {
                user.setRole("USER");
            }
        }

        // 6. 로그인 성공
        return user;
    }
}