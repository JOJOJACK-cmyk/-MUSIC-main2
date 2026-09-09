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
     * [신규] SRS on_publish 웹훅 - 실제 RTMP 송출이 시작되면 자동으로 상태 동기화
     * (방송자가 /api/broadcast/status를 따로 호출하지 않아도 실제 송출 기준으로 반영됨)
     */
    @Transactional
    public void handleStreamPublished(String streamKey) {

        broadcastRepository.findByStreamKey(streamKey)
                .ifPresentOrElse(broadcast -> {

                    boolean wasOn = "ON".equalsIgnoreCase(broadcast.getStatus());
                    broadcast.setStatus("ON");
                    broadcast.setStartedAt(LocalDateTime.now());
                    broadcast.setEndedAt(null);

                    log.info(
                            "[SRS] 방송 시작 감지 - broadcastId: {}, streamKey: {}",
                            broadcast.getId(), streamKey
                    );

                    if (!wasOn) {
                        notifyLiveStart(broadcast);
                    }

                }, () -> {
                    // OBS 에 넣은 스트림 키가 앱에서 발급한 현재 키와 다르면 여기로 온다.
                    String known = broadcastRepository.findAll().stream()
                            .map(b -> "id=" + b.getId() + " key=" + b.getStreamKey())
                            .collect(java.util.stream.Collectors.joining(" | "));
                    log.warn("[SRS] 알 수 없는 streamKey 로 publish - OBS 키가 앱의 현재 스트림 키와 다릅니다.\n"
                            + "   받은 키 : {}\n   DB 의 키 : {}", streamKey, known);
                });
    }

    /**
     * [신규] SRS on_unpublish 웹훅 - 실제 RTMP 송출이 끊기면 자동으로 상태 정리
     * (방송자가 OBS만 끄고 API를 안 불러도 DB 상태 및 신청곡/투표 데이터가 정리됨)
     */
    @Transactional
    public void handleStreamUnpublished(String streamKey) {

        broadcastRepository.findByStreamKey(streamKey)
                .ifPresentOrElse(broadcast -> {

                    broadcast.setStatus("OFF");
                    broadcast.setEndedAt(LocalDateTime.now());

                    // 방송 종료 시 신청곡 / 투표 Redis 데이터 정리
                    songVoteService.clearBroadcastVotes(broadcast.getId());

                    log.info(
                            "[SRS] 방송 종료 감지 - broadcastId: {}, streamKey: {}",
                            broadcast.getId(), streamKey
                    );

                }, () -> log.warn(
                        "[SRS] 알 수 없는 streamKey로 unpublish 이벤트 발생: {}", streamKey
                ));
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
            boolean srsHasIt = srsActive != null && srsActive.containsKey(b.getStreamKey());

            int viewers = srsHasIt
                    ? srsActive.get(b.getStreamKey())
                    : liveViewerService.getViewerCount(b.getId());

            // SRS 가 방송 화면을 2분마다 캡처해 두는 자동 썸네일 (1분 단위 캐시버스터).
            String snapshotUrl = hlsBaseUrl + "/live/" + b.getStreamKey() + ".jpg?v="
                    + (System.currentTimeMillis() / 60000);

            result.add(new LiveBroadcastResponse(
                    b.getId(),
                    b.getTitle(),
                    b.getUser().getNickname(),
                    b.getUser().getId(),
                    "LIVE",
                    viewers,
                    b.getThumbnailUrl(),
                    hlsBaseUrl + "/live/" + b.getStreamKey() + ".m3u8",
                    b.getStartedAt(),
                    b.getCategory(),
                    snapshotUrl,
                    b.isSongRequestEnabled()
            ));
        }
        return result;
    }
}