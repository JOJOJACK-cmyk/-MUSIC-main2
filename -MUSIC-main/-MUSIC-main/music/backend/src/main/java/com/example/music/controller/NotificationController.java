package com.example.music.controller;

import com.example.music.dto.NotificationDto;
import com.example.music.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notification API", description = "앱 내 실시간 알림")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final com.example.music.security.AuthenticatedUserResolver authenticatedUserResolver;

    @Operation(summary = "최근 알림 목록", description = "헤더 벨 아이콘에 표시할 최근 알림(최대 30건)을 조회합니다.")
    @GetMapping("/recent")
    public ResponseEntity<List<NotificationDto>> recent() {
        return ResponseEntity.ok(notificationService.getRecent());
    }

    @Operation(summary = "내 개인 알림", description = "로그인 사용자에게만 온 알림(이용권 만료 임박 등). 비로그인이면 빈 목록.")
    @GetMapping("/mine")
    public ResponseEntity<List<NotificationDto>> mine(org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(
                authenticatedUserResolver.resolveOptionalUser(authentication)
                        .map(u -> notificationService.getForUser(u.getId()))
                        .orElse(List.of()));
    }
}
