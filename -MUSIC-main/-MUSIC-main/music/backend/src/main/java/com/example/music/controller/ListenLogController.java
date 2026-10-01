package com.example.music.controller;

import com.example.music.dto.ListenLogDto;
import com.example.music.service.ListenLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.example.music.entity.User;
import com.example.music.security.AuthenticatedUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "ListenLog", description = "30초 청취 로그 수집 API")
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class ListenLogController {

    private final ListenLogService listenLogService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Operation(summary = "30초 청취 로그 수집", description = "프론트엔드 플레이어에서 30초 재생 시 호출합니다.")
    @PostMapping("/listen")
    public ResponseEntity<String> recordLog(
            @Valid @RequestBody ListenLogDto dto,
            Authentication authentication
    ) {
        // 요청 본문의 userId/email 은 신뢰하지 않는다 (다른 회원 명의로 차트 점수를 올리는 조작 방지).
        // 항상 인증된 사용자 기준으로 기록한다.
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        dto.setUserId(user.getId());
        dto.setEmail(user.getEmail());

        listenLogService.recordLog(dto);
        return ResponseEntity.ok("청취 로그가 정상적으로 기록되었습니다.");
    }
}
