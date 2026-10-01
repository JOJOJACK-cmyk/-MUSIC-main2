package com.example.music.controller;

import com.example.music.entity.User;
import com.example.music.security.AuthenticatedUserResolver;
import com.example.music.service.PlaylistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Playlist API", description = "내 플레이리스트 (로그인 필요)")
@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    private User me(Authentication authentication) {
        return authenticatedUserResolver.resolveRequiredUser(authentication);
    }

    @Operation(summary = "내 플레이리스트 목록")
    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        return playlistService.list(me(authentication));
    }

    @Operation(summary = "플레이리스트 만들기", description = "body: {name}")
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(playlistService.create(me(authentication), body.get("name")));
    }

    @Operation(summary = "플레이리스트 이름 변경", description = "body: {name}")
    @PatchMapping("/{id}")
    public Map<String, Object> rename(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication authentication) {
        return playlistService.rename(me(authentication), id, body.get("name"));
    }

    @Operation(summary = "플레이리스트 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        playlistService.delete(me(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "플레이리스트 상세 (곡 목록)")
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id, Authentication authentication) {
        return playlistService.detail(me(authentication), id);
    }

    @Operation(summary = "곡 담기", description = "body: {musicId}. 이미 담긴 곡이면 409")
    @PostMapping("/{id}/tracks")
    public ResponseEntity<?> addTrack(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        Long musicId;
        try {
            musicId = Long.valueOf(String.valueOf(body.get("musicId")));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "musicId 형식이 올바르지 않습니다."));
        }
        try {
            return ResponseEntity.ok(playlistService.addTrack(me(authentication), id, musicId));
        } catch (PlaylistService.DuplicateTrackException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(summary = "곡 빼기")
    @DeleteMapping("/{id}/tracks/{itemId}")
    public ResponseEntity<Void> removeTrack(@PathVariable Long id, @PathVariable Long itemId, Authentication authentication) {
        playlistService.removeTrack(me(authentication), id, itemId);
        return ResponseEntity.noContent().build();
    }
}
