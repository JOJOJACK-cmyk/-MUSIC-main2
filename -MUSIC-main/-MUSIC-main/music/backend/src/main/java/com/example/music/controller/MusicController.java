package com.example.music.controller;

import com.example.music.dto.MusicDto;
import com.example.music.service.MusicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/musics")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;

    // ==========================================
    // 1. 일반 사용자용 API (목록 및 차트 조회)
    // ==========================================
    @Tag(name = "Music API", description = "일반 청취자용 음원 및 차트 조회 API")
    @Operation(summary = "실시간 TOP 100 차트 조회", description = "실시간 음원 랭킹 및 TOP 100 차트 목록을 조회합니다.")
    @GetMapping("/ranking")
    public ResponseEntity<List<MusicDto.Response>> getRankingChart() {
        return ResponseEntity.ok(musicService.getAllMusic());
    }

    @Tag(name = "Music API", description = "일반 청취자용 음원 및 차트 조회 API")
    @Operation(summary = "전체 음원 목록 조회", description = "DB에 저장된 모든 음원 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<MusicDto.Response>> getAllMusic() {
        return ResponseEntity.ok(musicService.getAllMusic());
    }

    @Tag(name = "Music API", description = "일반 청취자용 음원 및 차트 조회 API")
    @Operation(summary = "음원 단건 조회", description = "음원 ID(PK)를 통해 특정 음원의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<MusicDto.Response> getMusic(
            @Parameter(description = "음원 고유 ID", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(musicService.getMusic(id));
    }


    // ==========================================
    // 2. 관리자 전용 API (음원 등록, 수정, 삭제)
    // ==========================================
    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] 음원 직접 등록", description = "제목, 아티스트, YouTube Video ID 등의 정보를 직접 입력하여 음원을 등록합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<MusicDto.Response> createMusic(@Valid @RequestBody MusicDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(musicService.createMusic(request));
    }

    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] YouTube 음원 메타데이터 연동 및 캐싱 등록", description = "YouTube Video ID를 기반으로 메타데이터를 조회하여 DB에 캐싱하고 음원으로 등록합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/youtube")
    public ResponseEntity<MusicDto.Response> createMusicFromYouTube(
            @Parameter(description = "YouTube 동영상 ID", example = "dQw4w9WgXcQ", required = true)
            @RequestParam String videoId) throws Exception {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(musicService.createMusicFromYouTube(videoId));
    }

    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] 음원 정보 수정", description = "기존 음원의 제목, 아티스트 등의 정보를 수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<MusicDto.Response> updateMusic(
            @Parameter(description = "수정할 음원 고유 ID", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody MusicDto.UpdateRequest request) {
        return ResponseEntity.ok(musicService.updateMusic(id, request));
    }

    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] 음원 삭제", description = "음원 ID를 받아 DB에서 해당 음원을 삭제합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMusic(
            @Parameter(description = "삭제할 음원 고유 ID", example = "1", required = true)
            @PathVariable Long id) {
        musicService.deleteMusic(id);
        return ResponseEntity.noContent().build();
    }
}