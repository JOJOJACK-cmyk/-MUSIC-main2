package com.example.music.controller;

import com.example.music.dto.ListenLogDto;
import com.example.music.service.ListenLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Tag(name = "ListenLog", description = "30초 청취 로그 수집 API")
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class ListenLogController {

    private final ListenLogService listenLogService;

    @Operation(summary = "30초 청취 로그 수집", description = "프론트엔드 플레이어에서 30초 재생 시 호출합니다.")
    @PostMapping("/listen")
    public ResponseEntity<String> recordLog(
            @Valid @RequestBody ListenLogDto dto,
            @AuthenticationPrincipal Object principal,
            Principal standardPrincipal
    ) {
        // DTO에 이메일이나 ID가 비어있고 Security 인증 세션이 있을 경우 세션 정보 주입
        if ((dto.getUserId() == null && (dto.getEmail() == null || dto.getEmail().isBlank()))) {
            if (principal instanceof OAuth2User oauth2User) {
                String email = oauth2User.getAttribute("email");
                if (email != null) {
                    dto.setEmail(email);
                }
            } else if (standardPrincipal != null) {
                dto.setEmail(standardPrincipal.getName());
            }
        }

        listenLogService.recordLog(dto);
        return ResponseEntity.ok("청취 로그가 정상적으로 기록되었습니다.");
    }
}