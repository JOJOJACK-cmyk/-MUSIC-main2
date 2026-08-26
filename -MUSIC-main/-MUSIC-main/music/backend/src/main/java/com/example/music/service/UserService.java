package com.example.music.service;

import com.example.music.dto.LoginRequestDto;
import com.example.music.dto.SignupRequestDto;
import com.example.music.entity.User;
import com.example.music.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    // 회원가입
    @Transactional
    public void signup(SignupRequestDto requestDto) {

        // 1. 이메일 중복 검사
        if (userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 2. 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(requestDto.getPassword());

        // 3. User 엔티티 생성
        User user = User.builder()
                .email(requestDto.getEmail())
                .password(encodedPassword)
                .nickname(requestDto.getNickname())
                .provider("local")
                .role("ROLE_USER")
                .build();

        // 4. DB 저장
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

        // 4. 로그인 성공
        return user;
    }
}