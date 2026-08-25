package com.example.music.controller;

import com.example.music.dto.ListenLogDto;
import com.example.music.service.ListenLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "ListenLog", description = "30초 청취 로그 수집 API")
@RestController
@RequestMapping("/api/v1/logs") // 💡 /api/logs -> /api/v1/logs 로 수정
@RequiredArgsConstructor
public class ListenLogController {

    private final ListenLogService listenLogService;

    @Operation(summary = "30초 청취 로그 수집", description = "프론트엔드 플레이어에서 30초 재생 시 호출합니다.")
    @PostMapping("/listen")
    public ResponseEntity<String> recordLog(@Valid @RequestBody ListenLogDto dto) {
        listenLogService.recordLog(dto);
        return ResponseEntity.ok("청취 로그가 정상적으로 기록되었습니다.");
    }
}