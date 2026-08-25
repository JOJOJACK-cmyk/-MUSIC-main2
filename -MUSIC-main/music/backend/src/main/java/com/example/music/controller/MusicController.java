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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Music API", description = "음원 CRUD 및 YouTube 메타데이터 연동/캐싱 API")
@RestController
@RequestMapping("/api/musics")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;

    @Operation(summary = "음원 직접 등록", description = "제목, 아티스트, YouTube Video ID 등의 정보를 직접 입력하여 음원을 등록합니다.")
    @PostMapping
    public ResponseEntity<MusicDto.Response> createMusic(@Valid @RequestBody MusicDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(musicService.createMusic(request));
    }

    @Operation(summary = "YouTube 음원 메타데이터 연동 및 캐싱 등록", description = "YouTube Video ID를 기반으로 메타데이터를 조회하여 DB에 캐싱하고 음원으로 등록합니다.")
    @PostMapping("/youtube")
    public ResponseEntity<MusicDto.Response> createMusicFromYouTube(
            @Parameter(description = "YouTube 동영상 ID (예: dQw4w9WgXcQ)", example = "dQw4w9WgXcQ", required = true)
            @RequestParam String videoId) throws Exception {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(musicService.createMusicFromYouTube(videoId));
    }

    // 🔴 [추가됨] 랭킹/차트 조회 엔드포인트
    // ⚠️ 주의: 동적 경로인 getMusic("/{id}") 보다 반드시 위쪽에 위치해야 "ranking" 문자열을 id로 오인하지 않습니다.
    @Operation(summary = "실시간 TOP 100 차트 조회", description = "실시간 음원 랭킹 및 TOP 100 차트 목록을 조회합니다.")
    @GetMapping("/ranking")
    public ResponseEntity<List<MusicDto.Response>> getRankingChart() {
        // 만약 MusicService에 랭킹 조회 메서드가 있다면 연결해주시고,
        // 우선 임시로 전체 목록이나 랭킹 데이터를 반환하도록 구성할 수 있습니다.
        // 예시: return ResponseEntity.ok(musicService.getRankingList());

        // 일단 기존 getAllMusic()을 활용하거나 랭킹 서비스로 연동하세요.
        return ResponseEntity.ok(musicService.getAllMusic());
    }

    @Operation(summary = "전체 음원 목록 조회", description = "DB에 저장된 모든 음원 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<MusicDto.Response>> getAllMusic() {
        return ResponseEntity.ok(musicService.getAllMusic());
    }

    @Operation(summary = "음원 단건 조회", description = "음원 ID(PK)를 통해 특정 음원의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<MusicDto.Response> getMusic(
            @Parameter(description = "음원 고유 ID", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(musicService.getMusic(id));
    }

    @Operation(summary = "음원 정보 수정", description = "기존 음원의 제목, 아티스트 등의 정보를 수정합니다.")
    @PutMapping("/{id}")
    public ResponseEntity<MusicDto.Response> updateMusic(
            @Parameter(description = "수정할 음원 고유 ID", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody MusicDto.UpdateRequest request) {
        return ResponseEntity.ok(musicService.updateMusic(id, request));
    }

    @Operation(summary = "음원 삭제", description = "음원 ID를 받아 DB에서 해당 음원을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMusic(
            @Parameter(description = "삭제할 음원 고유 ID", example = "1", required = true)
            @PathVariable Long id) {
        musicService.deleteMusic(id);
        return ResponseEntity.noContent().build();
    }
}