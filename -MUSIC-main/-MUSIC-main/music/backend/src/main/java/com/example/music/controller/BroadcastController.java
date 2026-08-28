package com.example.music.controller;

import com.example.music.entity.Broadcast;
import com.example.music.entity.User;
import com.example.music.service.BroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/broadcast")
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastService broadcastService;

    /**
     * 스트림 키 발급 및 재생성 API
     * POST /api/broadcast/stream-key
     */
    @PostMapping("/stream-key")
    public ResponseEntity<Broadcast> generateStreamKey(@RequestBody User user, @RequestParam(required = false) String defaultTitle) {
        // 실제 서비스에서는 @AuthenticationPrincipal 등을 통해 인증된 유저 정보를 주입받아 사용합니다.
        Broadcast broadcast = broadcastService.createOrUpdateStreamKey(user, defaultTitle);
        return ResponseEntity.ok(broadcast);
    }

    /**
     * 방송 정보(제목) 수정 API
     * PATCH /api/broadcast/info?userId=1&title=새로운방송제목
     */
    @PatchMapping("/info")
    public ResponseEntity<Void> updateBroadcastInfo(@RequestParam Long userId, @RequestParam String title) {
        broadcastService.updateBroadcastInfo(userId, title);
        return ResponseEntity.ok().build();
    }

    /**
     * 방송 상태(ON/OFF) 변경 API
     * PATCH /api/broadcast/status?userId=1&status=ON
     */
    @PatchMapping("/status")
    public ResponseEntity<Void> updateBroadcastStatus(@RequestParam Long userId, @RequestParam String status) {
        broadcastService.updateBroadcastStatus(userId, status);
        return ResponseEntity.ok().build();
    }
}