package com.example.music.controller;

import com.example.music.dto.ListenLogDto;
import com.example.music.service.ListenLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Log Collector", description = "30초 청취 로그 수집 API")
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class ListenLogController {

    private final ListenLogService listenLogService;

    @Operation(summary = "30초 청취 로그 기록", description =  "프론트엔드 오디오 플레이어에서 30초 이상 재생 시 호출되는 수집 트리거 API")
    @PostMapping("/listen")
    public ResponseEntity<String> collectLog(@RequestBody ListenLogDto dto) {
        listenLogService.recordLog(dto);
        return ResponseEntity.ok("청취 로그가 정상적으로 수집되었습니다.");
    }
}
