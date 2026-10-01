package com.example.music.controller;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import com.example.music.dto.SongVoteDto; // DTO 임포트 확인
import com.example.music.entity.Broadcast;
import com.example.music.entity.User;
import com.example.music.service.BroadcastService;
import com.example.music.service.LiveViewerService;
import com.example.music.service.SongVoteService;
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
    private final com.example.music.repository.MusicRepository musicRepository;
    private final com.example.music.repository.UserRepository userRepository;
    private final com.example.music.service.ChatModerationService chatModerationService;
    private final com.example.music.service.FollowService followService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final org.springframework.messaging.simp.SimpMessageSendingOperations messagingTemplate;

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
                    body.put("category", b.getCategory());
                    body.put("songRequestEnabled", b.isSongRequestEnabled());
                    body.put("streamKey", b.obsStreamKey()); // OBS 에 그대로 붙여넣는 값
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
        Boolean songReq = req.containsKey("songRequestEnabled")
                ? Boolean.valueOf(String.valueOf(req.get("songRequestEnabled")))
                : null;
        broadcastService.updateChannel(user, req.get("title"), req.get("description"),
                req.get("bannerUrl"), req.get("category"), songReq);
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
        body.put("streamKey", b.obsStreamKey()); // OBS 에 그대로 붙여넣는 값
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
     * 현재 투표 상태(옵션 순서 그대로, 번호 고정) 조회 — 페이지 로딩 시.
     */
    @GetMapping("/{broadcastId}/ranking")
    public ResponseEntity<List<SongVoteDto>> getRanking(@PathVariable Long broadcastId) {
        return ResponseEntity.ok(songVoteService.getPoll(broadcastId));
    }

    private boolean isBroadcasterOf(Long broadcastId, String email) {
        if (email == null || email.isBlank()) return false;
        return broadcastRepository.findWithUserById(broadcastId)
                .map(b -> email.equalsIgnoreCase(b.getUser().getEmail()))
                .orElse(false);
    }

    /**
     * [스트리머] 투표 곡 목록 설정 (REST).
     * body: {"options": ["직접 입력 제목", {"musicId": 12}, {"title": "곡B", "musicId": 34}, ...]}
     *  - musicId 가 있으면 카탈로그 곡의 "제목 - 아티스트"로 표시하고, 1위 시 그 곡을 바로 재생한다.
     *  - 카탈로그에 없는 musicId 는 title 만 남긴다 (title 도 없으면 제외).
     */
    @PutMapping("/{broadcastId}/poll")
    public ResponseEntity<?> setPoll(
            @PathVariable Long broadcastId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        if (!isBroadcasterOf(broadcastId, user.getEmail())) {
            return ResponseEntity.status(403).body(Map.of("message", "방송자만 설정할 수 있습니다."));
        }
        Object opts = body.get("options");
        List<SongVoteService.PollOption> options = new java.util.ArrayList<>();
        if (opts instanceof List<?> l) {
            for (Object o : l) {
                SongVoteService.PollOption parsed = parsePollOption(o);
                if (parsed != null) options.add(parsed);
            }
        }
        try {
            songVoteService.setPollOptions(broadcastId, options);
            List<SongVoteDto> poll = songVoteService.getPoll(broadcastId);
            messagingTemplate.convertAndSend("/topic/broadcast/" + broadcastId + "/ranking", poll);
            return ResponseEntity.ok(poll);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BroadcastController.class)
                    .error("[poll] setOptions 실패 broadcastId={}", broadcastId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "message", "투표 목록 저장 실패",
                    "error", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage())));
        }
    }

    private SongVoteService.PollOption parsePollOption(Object o) {
        if (o == null) return null;
        if (!(o instanceof Map<?, ?> m)) return SongVoteService.PollOption.text(String.valueOf(o));

        Object rawTitle = m.get("title");
        String title = rawTitle == null ? null : String.valueOf(rawTitle).trim();
        Long musicId = null;
        try {
            Object rawId = m.get("musicId");
            if (rawId != null) musicId = Long.valueOf(String.valueOf(rawId));
        } catch (NumberFormatException ignore) {}

        if (musicId != null) {
            var music = musicRepository.findById(musicId).orElse(null);
            if (music != null) {
                String label = music.getArtist() != null && !music.getArtist().isBlank()
                        ? music.getTitle() + " - " + music.getArtist()
                        : music.getTitle();
                return new SongVoteService.PollOption(label, music.getId());
            }
        }
        return (title == null || title.isEmpty()) ? null : SongVoteService.PollOption.text(title);
    }

    // ==========================================
    // 💬 [채팅 관리] 방송자 전용 — 메시지 삭제 / 채팅 금지
    // ==========================================

    /** [스트리머] 채팅 메시지 삭제. body: {"messageId": "..."} → 시청자 화면에서 해당 메시지 제거 */
    @PostMapping("/{broadcastId}/chat/delete")
    public ResponseEntity<?> deleteChatMessage(
            @PathVariable Long broadcastId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        if (!isBroadcasterOf(broadcastId, user.getEmail())) {
            return ResponseEntity.status(403).body(Map.of("message", "방송자만 메시지를 삭제할 수 있습니다."));
        }
        String messageId = body.get("messageId");
        if (messageId == null || messageId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "messageId 가 필요합니다."));
        }
        messagingTemplate.convertAndSend("/sub/chat/room/" + broadcastId,
                com.example.music.dto.ChatMessageDto.builder()
                        .roomId(String.valueOf(broadcastId))
                        .type(com.example.music.dto.ChatMessageDto.MessageType.DELETE)
                        .messageId(messageId)
                        .build());
        return ResponseEntity.ok().build();
    }

    /** [스트리머] 채팅 금지. body: {"userId": 12, "minutes": 10|60|720} */
    @PostMapping("/{broadcastId}/chat/mute")
    public ResponseEntity<?> muteChatUser(
            @PathVariable Long broadcastId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        if (!isBroadcasterOf(broadcastId, user.getEmail())) {
            return ResponseEntity.status(403).body(Map.of("message", "방송자만 채팅을 금지할 수 있습니다."));
        }
        Long targetId;
        int minutes;
        try {
            targetId = Long.valueOf(String.valueOf(body.get("userId")));
            minutes = Integer.parseInt(String.valueOf(body.getOrDefault("minutes", 10)));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "userId / minutes 형식이 올바르지 않습니다."));
        }
        if (!com.example.music.service.ChatModerationService.isAllowedMinutes(minutes)) {
            return ResponseEntity.badRequest().body(Map.of("message", "금지 시간은 10분, 60분, 720분 중 하나입니다."));
        }
        if (targetId.equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "본인은 채팅 금지할 수 없습니다."));
        }
        User target = userRepository.findById(targetId).orElse(null);
        if (target == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "사용자를 찾을 수 없습니다."));
        }

        chatModerationService.mute(broadcastId, targetId, minutes);
        String label = minutes >= 720 ? "이번 방송 동안" : (minutes >= 60 ? (minutes / 60) + "시간" : minutes + "분");
        sendChatNotice(broadcastId, AuthenticatedUserResolver.displayName(target) + " 님의 채팅이 " + label + " 금지되었어요");
        return ResponseEntity.ok(Map.of("userId", targetId, "minutes", minutes));
    }

    /** [스트리머] 채팅 금지 해제 */
    @DeleteMapping("/{broadcastId}/chat/mute/{userId}")
    public ResponseEntity<?> unmuteChatUser(
            @PathVariable Long broadcastId,
            @PathVariable Long userId,
            Authentication authentication) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        if (!isBroadcasterOf(broadcastId, user.getEmail())) {
            return ResponseEntity.status(403).body(Map.of("message", "방송자만 해제할 수 있습니다."));
        }
        chatModerationService.unmute(broadcastId, userId);
        userRepository.findById(userId).ifPresent(target ->
                sendChatNotice(broadcastId, AuthenticatedUserResolver.displayName(target) + " 님의 채팅 금지가 해제되었어요"));
        return ResponseEntity.ok().build();
    }

    private void sendChatNotice(Long broadcastId, String text) {
        messagingTemplate.convertAndSend("/sub/chat/room/" + broadcastId,
                com.example.music.dto.ChatMessageDto.builder()
                        .roomId(String.valueOf(broadcastId))
                        .type(com.example.music.dto.ChatMessageDto.MessageType.NOTICE)
                        .message(text)
                        .messageId(java.util.UUID.randomUUID().toString())
                        .timestamp(java.time.LocalDateTime.now().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .build());
    }

    /** [스트리머] 표만 초기화 (곡 목록 유지). */
    @PostMapping("/{broadcastId}/poll/reset")
    public ResponseEntity<?> resetPoll(@PathVariable Long broadcastId, Authentication authentication) {
        User user = authenticatedUserResolver.resolveRequiredUser(authentication);
        if (!isBroadcasterOf(broadcastId, user.getEmail())) {
            return ResponseEntity.status(403).body(Map.of("message", "방송자만 초기화할 수 있습니다."));
        }
        songVoteService.resetVotes(broadcastId);
        List<SongVoteDto> poll = songVoteService.getPoll(broadcastId);
        messagingTemplate.convertAndSend("/topic/broadcast/" + broadcastId + "/ranking", poll);
        return ResponseEntity.ok(poll);
    }

    // ==========================================
    // 🎵 [실시간 신청곡 및 투표 관련 WebSocket 엔드포인트]
    // ==========================================

    /**
     * 방송자가 [다음 곡으로 선택] 버튼을 눌렀을 때 실행
     * 클라이언트에서 /app/broadcast/next-song 으로 메시지를 보내면
     * /topic/broadcast/next-song 을 구독 중인 모든 시청자에게 전송됨
     */
    /**
     * 다음 곡 알림 payload. songTitle 을 채워서 보내면, 방송자 본인의 다른 창(예: OBS 채팅
     * 독)에서 눌러도 실제로 음악이 재생 중인 창(사이트 탭)이 이를 받아 재생하도록 프론트에서 사용한다.
     */
    public record NextSongMsg(String message, String songTitle, Long musicId) {}

    @MessageMapping("/broadcast/{broadcastId}/next-song")
    @SendTo("/topic/broadcast/{broadcastId}/next-song")
    public NextSongMsg selectNextSong(
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

        // 권한 확인 성공 후 1위 곡 선택 (카탈로그 곡이면 musicId 도 함께 → 재검색 없이 바로 재생)
        SongVoteDto top = songVoteService.getTop1(broadcastId);
        if (top == null) {
            return new NextSongMsg("등록된 신청곡이 없습니다.", null, null);
        }
        return new NextSongMsg("다음 곡: " + top.getSongTitle(), top.getSongTitle(), top.getMusicId());
    }
    /** 투표 버튼 payload: { number: 1 } (1-base). voter 는 무시하고 웹소켓 인증 사용자로 판정한다. */
    public record VoteMsg(String voter, Integer number) {}

    /**
     * 시청자 투표 (STOMP). 투표자는 Principal(로그인 사용자)로 정한다 — 채팅 "투표N" 과 같은
     * 식별자(사용자 PK)를 써서 한 사람 1표로 합산된다. 비로그인이면 반영하지 않는다.
     */
    @MessageMapping("/broadcast/{broadcastId}/vote")
    @SendTo("/topic/broadcast/{broadcastId}/ranking")
    public List<SongVoteDto> voteSong(
            @DestinationVariable Long broadcastId,
            VoteMsg msg,
            java.security.Principal principal
    ) {
        if (msg != null && msg.number() != null) {
            int index = msg.number() - 1; // 1-base → 0-base
            if (index >= 0) {
                authenticatedUserResolver.resolveOptionalUser(principal).ifPresent(user ->
                        songVoteService.vote(broadcastId, ChatController.voterId(user), index));
            }
        }
        return songVoteService.getPoll(broadcastId);
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
            @RequestParam(required = false) String viewerId,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        // 프론트가 탭마다 만든 랜덤 viewerId 로 시청자를 구분한다.
        // (예전엔 세션 ID 를 썼는데, 비로그인 시청자마다 7일짜리 세션이 생기고
        //  쿠키가 안 붙는 클라이언트는 요청마다 새 세션 = 시청자 수가 부풀려졌다)
        String id;
        if (viewerId != null && VIEWER_ID_PATTERN.matcher(viewerId).matches()) {
            id = "v:" + viewerId;
        } else {
            jakarta.servlet.http.HttpSession session = request.getSession(false);
            if (session == null) return ResponseEntity.badRequest().build();
            id = "s:" + session.getId();
        }

        liveViewerService.heartbeat(broadcastId, id);

        return ResponseEntity.ok().build();
    }

    private static final java.util.regex.Pattern VIEWER_ID_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9-]{8,64}$");

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