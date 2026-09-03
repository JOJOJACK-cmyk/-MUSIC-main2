package com.example.music.controller;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import com.example.music.dto.SongVoteDto; // DTO 임포트 확인
import com.example.music.entity.Broadcast;
import com.example.music.entity.User;
import com.example.music.service.BroadcastService;
import com.example.music.service.LiveViewerService;
import com.example.music.service.SongVoteService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;
import com.example.music.dto.LiveBroadcastResponse;
import java.util.List;
import com.example.music.repository.BroadcastRepository;
import com.example.music.security.AuthenticatedUserResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

@RestController
@RequestMapping("/api/broadcast")
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastService broadcastService;
    private final SongVoteService songVoteService;
    private final LiveViewerService liveViewerService;
    private final BroadcastRepository broadcastRepository;
    private final com.example.music.service.FollowService followService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    // OBS 등 인코더가 송출할 RTMP 서버 주소 (SRS 기본값)
    @org.springframework.beans.factory.annotation.Value("${srs.rtmp-url:rtmp://localhost:1935/live}")
    private String rtmpIngestUrl;

    /**
     * 내 방송 정보 조회 API
     * GET /api/broadcast/mine
     * 프로필 설정창의 "스트리밍 설정"에서 현재 스트림키/제목/상태를 표시하기 위해 사용.
     * 아직 방송을 만든 적이 없으면 204 No Content.
     */
    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> getMyBroadcast(Authentication authentication) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("ingestUrl", rtmpIngestUrl);
        body.put("nickname", user.getNickname());
        body.put("profileImageUrl", user.getProfileImageUrl());
        body.put("followerCount", followService.followerCount(user.getId()));
        return broadcastRepository.findByUser_Id(user.getId())
                .<ResponseEntity<Map<String, Object>>>map(b -> {
                    body.put("id", b.getId());
                    body.put("title", b.getTitle());
                    body.put("description", b.getDescription());
                    body.put("bannerUrl", b.getBannerUrl());
                    body.put("streamKey", b.getStreamKey());
                    body.put("status", b.getStatus());
                    body.put("startedAt", b.getStartedAt());
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.ok(body)); // 방송 미생성이어도 ingestUrl 은 안내
    }

    /** 채널 정보(제목·소개글·배너) 일괄 수정 */
    @PatchMapping("/channel")
    public ResponseEntity<Void> updateChannel(
            @RequestBody Map<String, String> req,
            Authentication authentication
    ) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        broadcastService.updateChannel(user, req.get("title"), req.get("description"), req.get("bannerUrl"));
        return ResponseEntity.ok().build();
    }

    /**
     * 스트림 키 발급 및 재생성 API
     * POST /api/broadcast/stream-key
     * [수정] 클라이언트가 보낸 User 객체를 신뢰하지 않고, 로그인 세션 기준으로 본인 것만 발급/재발급
     */
    @PostMapping("/stream-key")
    public ResponseEntity<Map<String, Object>> generateStreamKey(
            @RequestParam(required = false) String defaultTitle,
            Authentication authentication
    ) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        Broadcast b = broadcastService.createOrUpdateStreamKey(user, defaultTitle);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", b.getId());
        body.put("title", b.getTitle());
        body.put("streamKey", b.getStreamKey());
        body.put("status", b.getStatus());
        return ResponseEntity.ok(body);
    }

    /**
     * 방송 정보(제목) 수정 API
     * PATCH /api/broadcast/info?title=새로운방송제목
     * [수정] userId를 파라미터로 받지 않고, 로그인한 본인의 방송만 수정 가능
     */
    @PatchMapping("/info")
    public ResponseEntity<Void> updateBroadcastInfo(
            @RequestParam String title,
            Authentication authentication
    ) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        broadcastService.updateBroadcastInfo(user.getId(), title);
        return ResponseEntity.ok().build();
    }

    /**
     * 방송 상태(ON/OFF) 변경 API
     * PATCH /api/broadcast/status?status=ON
     * [수정] userId를 파라미터로 받지 않고, 로그인한 본인의 방송만 상태 변경 가능
     */
    @PatchMapping("/status")
    public ResponseEntity<Void> updateBroadcastStatus(
            @RequestParam String status,
            Authentication authentication
    ) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        broadcastService.updateBroadcastStatus(user.getId(), status);
        return ResponseEntity.ok().build();
    }

    /**
     * 🌟 [추가됨] 초기 화면 로딩용 실시간 투표 순위 조회 REST API
     * GET /api/broadcast/ranking
     */
    @GetMapping("/{broadcastId}/ranking")
    public ResponseEntity<List<SongVoteDto>> getRanking(
            @PathVariable Long broadcastId
    ) {

        List<SongVoteDto> rankings =
                songVoteService.getTopSongRankings(broadcastId, 10);

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
    @MessageMapping("/broadcast/{broadcastId}/next-song")
    @SendTo("/topic/broadcast/{broadcastId}/next-song")
    public String selectNextSong(
            @DestinationVariable Long broadcastId,
            Authentication authentication
    ) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new AccessDeniedException(
                    "로그인이 필요합니다."
            );
        }

        // 현재 로그인 사용자 이메일
        String loginEmail =
                extractLoginEmail(authentication);

        if (loginEmail == null ||
                loginEmail.isBlank()) {

            throw new AccessDeniedException(
                    "로그인 사용자 정보를 확인할 수 없습니다."
            );
        }

        // 방송 + 방송 소유자 조회
        Broadcast broadcast =
                broadcastRepository
                        .findWithUserById(broadcastId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "방송을 찾을 수 없습니다."
                                )
                        );

        // 방송자 이메일
        String broadcasterEmail =
                broadcast.getUser().getEmail();

        // 현재 로그인 사용자 != 방송자
        if (!loginEmail.equalsIgnoreCase(
                broadcasterEmail
        )) {
            throw new AccessDeniedException(
                    "방송자만 다음 곡을 선택할 수 있습니다."
            );
        }

        // 권한 확인 성공 후 1위 곡 선택
        String nextSong =
                songVoteService.getTop1Song(
                        broadcastId
                );

        return "다음 곡: " + nextSong;
    }
    /**
     * 시청자가 특정 곡에 투표(좋아요)할 때 실행
     * 클라이언트에서 /app/broadcast/vote 로 곡 제목을 보냄
     * 투표 반영 후, 갱신된 최신 순위 리스트를 /topic/broadcast/ranking 을 구독 중인 모두에게 브로드캐스트
     */
    @MessageMapping("/broadcast/{broadcastId}/vote")
    @SendTo("/topic/broadcast/{broadcastId}/ranking")
    public List<SongVoteDto> voteSong(
            @DestinationVariable Long broadcastId,
            String songTitle,
            Authentication authentication
    ) {

        // 로그인 여부 확인
        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new AccessDeniedException(
                    "로그인 후 투표할 수 있습니다."
            );
        }

        // 현재 로그인 사용자 이메일
        String loginEmail =
                extractLoginEmail(authentication);

        if (loginEmail == null ||
                loginEmail.isBlank()) {

            throw new AccessDeniedException(
                    "로그인 사용자 정보를 확인할 수 없습니다."
            );
        }

        // 투표 시도
        boolean voted =
                songVoteService.voteSong(
                        broadcastId,
                        loginEmail,
                        songTitle
                );

        // 이미 투표한 곡이면 점수는 올라가지 않음
        if (!voted) {
            System.out.println(
                    "중복 투표 차단: "
                            + loginEmail
                            + " / "
                            + songTitle
            );
        }

        // 최신 순위 반환
        return songVoteService.getTopSongRankings(
                broadcastId,
                10
        );
    }
    /**
     * 현재 실제 송출 중인 방송 목록 조회
     * GET /api/broadcast/live
     */
    @GetMapping("/live")
    public ResponseEntity<List<LiveBroadcastResponse>> getLiveBroadcasts() {

        return ResponseEntity.ok(
                broadcastService.getLiveBroadcasts()
        );
    }
    /**
     * 라이브 시청자 heartbeat
     * POST /api/broadcast/{broadcastId}/viewers/heartbeat
     */
    @PostMapping("/{broadcastId}/viewers/heartbeat")
    public ResponseEntity<Void> viewerHeartbeat(
            @PathVariable Long broadcastId,
            HttpSession session
    ) {

        liveViewerService.heartbeat(
                broadcastId,
                session.getId()
        );

        return ResponseEntity.ok().build();
    }
    private String extractLoginEmail(
            Authentication authentication
    ) {

        Object principal =
                authentication.getPrincipal();

        // OAuth2 로그인
        if (principal instanceof OAuth2User oauth2User) {

            Map<String, Object> attributes =
                    oauth2User.getAttributes();

            // 카카오
            if (attributes.containsKey(
                    "kakao_account"
            )) {

                Map<String, Object> kakaoAccount =
                        (Map<String, Object>)
                                attributes.get(
                                        "kakao_account"
                                );

                return (String)
                        kakaoAccount.get("email");
            }

            // 네이버
            if (attributes.containsKey(
                    "response"
            )) {

                Map<String, Object> response =
                        (Map<String, Object>)
                                attributes.get(
                                        "response"
                                );

                return (String)
                        response.get("email");
            }

            // 구글
            return (String)
                    attributes.get("email");
        }

        // 일반 로그인
        return authentication.getName();
    }
}