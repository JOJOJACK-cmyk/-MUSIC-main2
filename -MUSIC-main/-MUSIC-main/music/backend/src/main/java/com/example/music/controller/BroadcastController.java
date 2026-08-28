package com.example.music.controller;

import com.example.music.dto.SongVoteDto; // DTO 임포트 확인
import com.example.music.entity.Broadcast;
import com.example.music.entity.User;
import com.example.music.service.BroadcastService;
import com.example.music.service.SongVoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/broadcast")
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastService broadcastService;
    private final SongVoteService songVoteService;

    /**
     * 스트림 키 발급 및 재생성 API
     * POST /api/broadcast/stream-key
     */
    @PostMapping("/stream-key")
    public ResponseEntity<Broadcast> generateStreamKey(@RequestBody User user, @RequestParam(required = false) String defaultTitle) {
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

    /**
     * 🌟 [추가됨] 초기 화면 로딩용 실시간 투표 순위 조회 REST API
     * GET /api/broadcast/ranking
     */
    @GetMapping("/ranking")
    public ResponseEntity<List<SongVoteDto>> getRanking() {
        List<SongVoteDto> rankings = songVoteService.getTopSongRankings(10); // 상위 10개 조회
        return ResponseEntity.ok(rankings);
    }

    // ==========================================
    // 🎵 [실시간 신청곡 및 투표 관련 WebSocket 엔드포인트]
    // ==========================================

    /**
     * 방송자가 [다음 곡으로 선택] 버튼을 눌렀을 때 실행
     * 클라이언트에서 /app/broadcast/next-song 으로 메시지를 보내면
     * /topic/broadcast/next-song 을 구독 중인 모든 시청자에게 전송됨
     */
    @MessageMapping("/broadcast/next-song")
    @SendTo("/topic/broadcast/next-song")
    public String selectNextSong() {
        // Redis 실시간 투표 1위 곡을 자동 선정
        String nextSong = songVoteService.getTop1Song();
        return "다음 곡: " + nextSong;
    }

    /**
     * 시청자가 특정 곡에 투표(좋아요)할 때 실행
     * 클라이언트에서 /app/broadcast/vote 로 곡 제목을 보냄
     * 투표 반영 후, 갱신된 최신 순위 리스트를 /topic/broadcast/ranking 을 구독 중인 모두에게 브로드캐스트
     */
    @MessageMapping("/broadcast/vote")
    @SendTo("/topic/broadcast/ranking")
    public List<SongVoteDto> voteSong(String songTitle) {
        // 1. 투표 점수 반영
        songVoteService.voteSong(songTitle);

        // 2. 갱신된 상위 순위 리스트를 바로 반환하여 시청자 화면들의 순위를 실시간 동기화
        return songVoteService.getTopSongRankings(10);
    }
}