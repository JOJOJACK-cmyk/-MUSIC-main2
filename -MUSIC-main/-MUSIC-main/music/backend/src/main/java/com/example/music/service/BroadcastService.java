package com.example.music.service;

import com.example.music.dto.LiveBroadcastResponse;
import com.example.music.entity.Broadcast;
import com.example.music.entity.User;
import com.example.music.repository.BroadcastRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final BroadcastRepository broadcastRepository;
    private final SrsLiveStatusService srsLiveStatusService;
    private final LiveViewerService liveViewerService;
    private final SongVoteService songVoteService;
    private final NotificationService notificationService;

    // [수정] HLS 주소를 하드코딩하지 않고 환경별로 설정 가능하게 분리 (기본값: 로컬 개발환경)
    @Value("${hls.base-url:http://localhost:8081}")
    private String hlsBaseUrl;

    /**
     * 스트림 키 발급 및 재생성
     */
    @Transactional
    public Broadcast createOrUpdateStreamKey(
            User user,
            String defaultTitle
    ) {

        // 유저 ID로 기존 방송 정보 조회, 없으면 새로 생성
        Broadcast broadcast =
                broadcastRepository
                        .findByUser_Id(user.getId())
                        .orElseGet(() -> {

                            Broadcast newBroadcast =
                                    new Broadcast();

                            newBroadcast.setUser(user);

                            String title =
                                    defaultTitle != null
                                            ? defaultTitle
                                            : user.getNickname()
                                            + "의 방송국";

                            newBroadcast.setTitle(title);
                            newBroadcast.setStatus("OFF");
                            newBroadcast.setCreatedAt(
                                    LocalDateTime.now()
                            );

                            return newBroadcast;
                        });

        // 고유 스트림 키 생성
        String uniqueStreamKey =
                "live_"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        broadcast.setStreamKey(
                uniqueStreamKey
        );
        if (broadcast.getPlaybackId() == null) {
            broadcast.setPlaybackId(Broadcast.newPlaybackId());
        }

        return broadcastRepository.save(
                broadcast
        );
    }

    /** 채널 정보(제목/소개글/배너/카테고리/신청곡사용) 일괄 수정. 방송 정보가 없으면 새로 생성. */
    @Transactional
    public void updateChannel(User user, String title, String description, String bannerUrl,
                             String category, Boolean songRequestEnabled) {
        Broadcast b = broadcastRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    Broadcast nb = new Broadcast();
                    nb.setUser(user);
                    nb.setStatus("OFF");
                    nb.setStreamKey("live_" + UUID.randomUUID().toString().replace("-", ""));
                    nb.setPlaybackId(Broadcast.newPlaybackId());
                    nb.setCreatedAt(LocalDateTime.now());
                    return nb;
                });
        if (title != null && !title.isBlank()) b.setTitle(title.trim());
        if (b.getTitle() == null || b.getTitle().isBlank()) b.setTitle(user.getNickname() + "의 방송국");
        if (description != null) b.setDescription(description.isBlank() ? null : description.trim());
        if (bannerUrl != null) b.setBannerUrl(bannerUrl.isBlank() ? null : bannerUrl.trim());
        if (category != null) b.setCategory(category.isBlank() ? null : category.trim());
        if (songRequestEnabled != null) b.setSongRequestEnabled(songRequestEnabled);
        broadcastRepository.save(b);
    }

    /**
     * 채팅 명령("!투표창" 등)으로 신청곡 투표창을 켠다. 방송자 본인만 가능.
     * @return true = 켜짐(이미 켜져 있었어도 true), false = 방송을 찾을 수 없거나 방송자가 아님
     */
    @Transactional
    public boolean enableSongRequestByChatCommand(Long broadcastId, Long senderUserId) {
        return setSongRequestByChatCommand(broadcastId, senderUserId, true);
    }

    /**
     * 채팅 명령("!투표창닫기" 등)으로 신청곡 투표창을 끈다. 방송자 본인만 가능.
     * @return true = 꺼짐(이미 꺼져 있었어도 true), false = 방송을 찾을 수 없거나 방송자가 아님
     */
    @Transactional
    public boolean disableSongRequestByChatCommand(Long broadcastId, Long senderUserId) {
        return setSongRequestByChatCommand(broadcastId, senderUserId, false);
    }

    // 방송자 본인 여부는 닉네임이 아니라 인증된 사용자 PK 로 비교한다 (닉네임 사칭 방지)
    private boolean setSongRequestByChatCommand(Long broadcastId, Long senderUserId, boolean enabled) {
        if (broadcastId == null || senderUserId == null) return false;
        Broadcast b = broadcastRepository.findWithUserById(broadcastId).orElse(null);
        if (b == null || b.getUser() == null) return false;
        if (!senderUserId.equals(b.getUser().getId())) return false;
        if (b.isSongRequestEnabled() != enabled) {
            b.setSongRequestEnabled(enabled);
            broadcastRepository.save(b);
        }
        return true;
    }

    /**
     * 방송 제목 수정
     */
    @Transactional
    public void updateBroadcastInfo(
            Long userId,
            String title
    ) {

        Broadcast broadcast =
                broadcastRepository
                        .findByUser_Id(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "해당 유저의 방송 정보를 찾을 수 없습니다."
                                        )
                        );

        broadcast.setTitle(title);
    }

    /**
     * 방송 상태 변경
     */
    @Transactional
    public void updateBroadcastStatus(
            Long userId,
            String status
    ) {

        Broadcast broadcast =
                broadcastRepository
                        .findByUser_Id(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "해당 유저의 방송 정보를 찾을 수 없습니다."
                                        )
                        );

        String prevStatus = broadcast.getStatus();
        broadcast.setStatus(status);

        // 방송 시작
        if ("ON".equalsIgnoreCase(status)) {

            broadcast.setStartedAt(
                    LocalDateTime.now()
            );

            broadcast.setEndedAt(null);

            // 이전에 ON 이 아니었을 때만 "라이브 시작" 알림 발행
            if (!"ON".equalsIgnoreCase(prevStatus)) {
                notifyLiveStart(broadcast);
            }
        }

        // 방송 종료
        else if ("OFF".equalsIgnoreCase(status)) {

            broadcast.setEndedAt(
                    LocalDateTime.now()
            );

            // 방송 종료 시 신청곡 / 투표 Redis 데이터 정리
            songVoteService.clearBroadcastVotes(
                    broadcast.getId()
            );
        }
    }

    /**
     * 공개 재생 ID 가 없는 기존 방송(playback_id 컬럼 추가 이전 데이터)에 ID 를 채운다.
     * 이 ID 가 생기기 전에는 스트림 키가 곧 HLS 파일명이라 시청자에게 키가 노출됐었다.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void backfillPlaybackIds() {
        List<Broadcast> missing = broadcastRepository.findByPlaybackIdIsNull();
        for (Broadcast b : missing) {
            b.setPlaybackId(Broadcast.newPlaybackId());
        }
        if (!missing.isEmpty()) {
            broadcastRepository.saveAll(missing);
            log.info("[Broadcast] playbackId 백필 {}건 - 방송자는 OBS 스트림 키를 다시 복사해야 합니다.", missing.size());
        }
    }

    /**
     * SRS on_publish 웹훅 - 송출 허용 여부 판단 + 상태 동기화.
     * OBS 스트림 키는 "{playbackId}?key={streamKey}" 형태이므로, SRS 가 넘겨준
     * stream(=playbackId) 으로 방송을 찾고 param 의 key 가 비밀 스트림 키와 일치해야만 허용한다.
     *
     * @return true = 송출 허용, false = 거부 (등록되지 않은 방송이거나 키 불일치)
     */
    @Transactional
    public boolean handleStreamPublished(String stream, String param) {
        Broadcast broadcast = broadcastRepository.findByPlaybackId(stream).orElse(null);
        String key = extractParam(param, "key");
        if (broadcast == null || key == null || !constantTimeEquals(broadcast.getStreamKey(), key)) {
            // 키는 로그에 남기지 않는다 (로그 유출 = 방송 탈취)
            log.warn("[SRS] 송출 거부 - 등록되지 않은 스트림이거나 키 불일치: stream={}", stream);
            return false;
        }

        boolean wasOn = "ON".equalsIgnoreCase(broadcast.getStatus());
        broadcast.setStatus("ON");
        broadcast.setStartedAt(LocalDateTime.now());
        broadcast.setEndedAt(null);

        log.info("[SRS] 방송 시작 감지 - broadcastId: {}, stream: {}", broadcast.getId(), stream);

        if (!wasOn) {
            notifyLiveStart(broadcast);
        }
        return true;
    }

    /**
     * SRS on_unpublish 웹훅 - 실제 RTMP 송출이 끊기면 자동으로 상태 정리
     * (방송자가 OBS만 끄고 API를 안 불러도 DB 상태 및 신청곡/투표 데이터가 정리됨)
     */
    @Transactional
    public void handleStreamUnpublished(String stream) {

        broadcastRepository.findByPlaybackId(stream)
                .ifPresentOrElse(broadcast -> {

                    broadcast.setStatus("OFF");
                    broadcast.setEndedAt(LocalDateTime.now());

                    // 방송 종료 시 신청곡 / 투표 Redis 데이터 정리
                    songVoteService.clearBroadcastVotes(broadcast.getId());

                    log.info("[SRS] 방송 종료 감지 - broadcastId: {}, stream: {}", broadcast.getId(), stream);

                }, () -> log.warn("[SRS] 알 수 없는 stream 으로 unpublish 이벤트 발생: {}", stream));
    }

    /** "?key=abc&x=y" 형태의 SRS param 에서 값 추출 */
    private static String extractParam(String param, String name) {
        if (param == null || param.isBlank()) return null;
        String q = param.startsWith("?") ? param.substring(1) : param;
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** "OO님이 라이브를 시작했어요" 앱 내 알림 발행 (broadcasterId 포함 → 프론트에서 팔로우 여부 판별) */
    private void notifyLiveStart(Broadcast broadcast) {
        try {
            Long hostId = broadcast.getUser() != null ? broadcast.getUser().getId() : null;
            String host = broadcast.getUser() != null ? broadcast.getUser().getNickname() : "누군가";
            notificationService.publish(
                    "LIVE_START",
                    host + "님이 라이브를 시작했어요",
                    broadcast.getTitle() != null ? broadcast.getTitle() : "지금 방송 중",
                    "/live/" + broadcast.getId(),
                    hostId
            );
        } catch (Exception e) {
            log.warn("라이브 시작 알림 발행 실패: {}", e.getMessage());
        }
    }

    /**
     * 현재 방송 중인 목록 조회.
     *  - 기준은 DB status="ON" (SRS on_publish/on_unpublish 웹훅이 관리하는 값).
     *  - SRS API 는 (1) 실시간 시청자 수, (2) 송출이 끊긴 유령 방송 정리에만 보조로 사용한다.
     *    (예전처럼 "SRS API 의 stream name 과 정확히 일치" 를 필수 조건으로 두면, 키 표기가
     *     조금만 달라도/SRS API 가 잠깐 흔들려도 방송이 목록에서 통째로 사라진다.)
     */
    @Transactional(readOnly = true)
    public List<LiveBroadcastResponse> getLiveBroadcasts() {

        List<Broadcast> onAir = broadcastRepository.findByStatusIgnoreCase("ON");
        if (onAir.isEmpty()) return List.of();

        // SRS 실제 송출 현황 {streamKey -> 시청자수}. 시청자 수 표시에만 쓴다.
        //  ⚠️ SRS API 가 잠깐 흔들려서 목록이 비게 오면 정상 방송이 목록에서 사라지므로,
        //     여기서 status 를 OFF 로 바꾸지 않는다. OFF 는 SRS on_unpublish 웹훅이 담당.
        java.util.Map<String, Integer> srsActive = null;
        try {
            srsActive = srsLiveStatusService.getActiveStreamsWithViewerCount();
        } catch (Exception e) {
            log.debug("SRS 미연결 - status=ON 기준으로만 라이브 목록 구성: {}", e.getMessage());
        }

        List<LiveBroadcastResponse> result = new java.util.ArrayList<>();
        for (Broadcast b : onAir) {
            // 공개 URL 에는 비밀 스트림 키가 아니라 재생 ID 만 쓴다
            String playbackId = b.getPlaybackId();
            if (playbackId == null) continue; // 백필 전 데이터 (기동 시 채워짐)

            boolean srsHasIt = srsActive != null && srsActive.containsKey(playbackId);

            int viewers = srsHasIt
                    ? srsActive.get(playbackId)
                    : liveViewerService.getViewerCount(b.getId());

            // SRS 가 방송 화면을 2분마다 캡처해 두는 자동 썸네일 (1분 단위 캐시버스터).
            String snapshotUrl = hlsBaseUrl + "/live/" + playbackId + ".jpg?v="
                    + (System.currentTimeMillis() / 60000);

            result.add(new LiveBroadcastResponse(
                    b.getId(),
                    b.getTitle(),
                    b.getUser().getNickname(),
                    b.getUser().getId(),
                    "LIVE",
                    viewers,
                    b.getThumbnailUrl(),
                    hlsBaseUrl + "/live/" + playbackId + ".m3u8",
                    b.getStartedAt(),
                    b.getCategory(),
                    snapshotUrl,
                    b.isSongRequestEnabled()
            ));
        }
        return result;
    }
}