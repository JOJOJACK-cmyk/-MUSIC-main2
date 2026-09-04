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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/musics")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;

    // ==========================================
    // 1. 일반 사용자용 API (목록, 차트 조회 및 좋아요)
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

    // 💡 엑셀/DB 전체 대상 키워드 검색 API 추가
    @Tag(name = "Music API", description = "일반 청취자용 음원 및 차트 조회 API")
    @Operation(summary = "음원 키워드 검색", description = "제목 또는 아티스트에 특정 검색어가 포함된 DB 전체 음원 목록을 조회합니다.")
    @GetMapping("/search")
    public ResponseEntity<List<MusicDto.Response>> searchMusics(
            @Parameter(description = "검색 키워드", example = "르세라핌", required = true)
            @RequestParam String keyword) {
        return ResponseEntity.ok(musicService.searchMusics(keyword));
    }

    // 💡 내 보관함(좋아요 누른 음악 목록) 조회 API
    @Tag(name = "Music API", description = "일반 청취자용 음원 및 차트 조회 API")
    @Operation(summary = "내 보관함 좋아요 목록 조회", description = "현재 로그인한 유저가 좋아요를 누른 음악 목록을 조회합니다.")
    @GetMapping("/liked")
    public ResponseEntity<?> getLikedMusics(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요한 서비스입니다.");
        }

        List<MusicDto.Response> likedMusics = musicService.getLikedMusics(authentication);
        return ResponseEntity.ok(likedMusics);
    }

    // 💡 음원 좋아요(내 보관함 담기/취소) 토글 API
    @Tag(name = "Music API", description = "일반 청취자용 음원 및 차트 조회 API")
    @Operation(summary = "음원 좋아요 토글", description = "특정 음원에 대한 좋아요(내 보관함 등록/취소)를 수행합니다.")
    @PostMapping("/{id}/like")
    public ResponseEntity<?> toggleLikeMusic(
            @Parameter(description = "음원 고유 ID", example = "1", required = true)
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요한 서비스입니다.");
        }

        boolean isLiked = musicService.toggleLikeMusic(authentication, id);

        Map<String, Object> response = new HashMap<>();
        response.put("liked", isLiked);
        response.put("message", isLiked ? "내 보관함에 추가되었습니다." : "보관함에서 취소되었습니다.");

        return ResponseEntity.ok(response);
    }


    // ==========================================
    // 2. 관리자 전용 API (음원 등록, 수정, 삭제)
    // ==========================================
    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] 음원 직접 등록", description = "제목, 아티스트, YouTube Video ID 등의 정보를 직접 입력하여 음원을 등록합니다.")
    @PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN')")
    @PostMapping
    public ResponseEntity<MusicDto.Response> createMusic(@Valid @RequestBody MusicDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(musicService.createMusic(request));
    }

    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] YouTube 음원 메타데이터 연동 및 캐싱 등록", description = "YouTube Video ID를 기반으로 메타데이터를 조회하여 DB에 캐싱하고 음원으로 등록합니다.")
    @PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN')")
    @PostMapping("/youtube")
    public ResponseEntity<MusicDto.Response> createMusicFromYouTube(
            @Parameter(description = "YouTube 동영상 ID", example = "dQw4w9WgXcQ", required = true)
            @RequestParam String videoId) throws Exception {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(musicService.createMusicFromYouTube(videoId));
    }

    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] 카테고리별 유튜브 최신곡 일괄 동기화",
            description = "KPOP/JPOP/VTUBER/POP 카테고리별 키워드로 유튜브 최신 음악을 검색하여 " +
                    "쇼츠/장편 영상을 걸러낸 단곡만 DB에 등록합니다. (스케줄러가 매일 새벽 3시에 자동 수행)")
    @PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN')")
    @PostMapping("/youtube/sync-latest")
    public ResponseEntity<Map<String, Integer>> syncLatestMusic(
            @Parameter(description = "카테고리 키워드당 검색 결과 수", example = "15")
            @RequestParam(defaultValue = "15") int perKeyword) {
        return ResponseEntity.ok(musicService.syncLatestMusicForAllCategories(perKeyword));
    }

    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] 지역별 인기 음악 차트 동기화",
            description = "YouTube videos.list(chart=mostPopular)로 KR/JP/US 인기 음악을 가져와 등록합니다. " +
                    "search API 할당량을 소진하지 않아(호출당 1유닛) 언제든 실행 가능합니다.")
    @PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN')")
    @PostMapping("/youtube/sync-trending")
    public ResponseEntity<Map<String, Integer>> syncTrendingMusic() {
        return ResponseEntity.ok(musicService.syncTrendingMusic());
    }

    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] 카탈로그 재검증(비음악 영상 정리)",
            description = "DB의 모든 곡을 videos.list 로 다시 확인해 '진짜 곡' 기준을 못 통과하는 항목(인터뷰·라이브클립·쇼츠 등)을 삭제합니다. videos.list 는 저렴(50건당 1유닛).")
    @PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN')")
    @PostMapping("/admin/revalidate")
    public ResponseEntity<Map<String, Integer>> revalidateCatalog() {
        return ResponseEntity.ok(musicService.pruneNonMusicCatalog());
    }

    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] 음원 정보 수정", description = "기존 음원의 제목, 아티스트 등의 정보를 수정합니다.")
    @PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<MusicDto.Response> updateMusic(
            @Parameter(description = "수정할 음원 고유 ID", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody MusicDto.UpdateRequest request) {
        return ResponseEntity.ok(musicService.updateMusic(id, request));
    }

    @Tag(name = "Admin Music API", description = "관리자 전용 음원 등록 및 관리 시스템 API")
    @Operation(summary = "[관리자] 음원 삭제", description = "음원 ID를 받아 DB에서 해당 음원을 삭제합니다.")
    @PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMusic(
            @Parameter(description = "삭제할 음원 고유 ID", example = "1", required = true)
            @PathVariable Long id) {
        musicService.deleteMusic(id);
        return ResponseEntity.noContent().build();
    }
}