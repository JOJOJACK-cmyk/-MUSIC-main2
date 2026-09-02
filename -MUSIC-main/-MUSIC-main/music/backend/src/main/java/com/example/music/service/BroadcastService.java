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

        broadcast.setStatus(status);

        // 방송 시작
        if ("ON".equalsIgnoreCase(status)) {

            broadcast.setStartedAt(
                    LocalDateTime.now()
            );

            broadcast.setEndedAt(null);

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

                    broadcast.setStatus("ON");
                    broadcast.setStartedAt(LocalDateTime.now());
                    broadcast.setEndedAt(null);

                    log.info(
                            "[SRS] 방송 시작 감지 - broadcastId: {}, streamKey: {}",
                            broadcast.getId(), streamKey
                    );

                }, () -> log.warn(
                        "[SRS] 알 수 없는 streamKey로 publish 이벤트 발생: {}", streamKey
                ));
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

    /**
     * 현재 실제 송출 중인 방송 목록 조회
     */
    @Transactional(readOnly = true)
    public List<LiveBroadcastResponse>
    getLiveBroadcasts() {

        // SRS에서 현재 실제 송출 중인 스트림 키 조회
        // [수정] SRS가 응답하지 않아도(재시작 중/장애) 전체 API가 500으로 죽지 않도록 방어
        List<String> activeStreamKeys;
        try {
            activeStreamKeys = srsLiveStatusService.getActiveStreamKeys();
        } catch (IllegalStateException e) {
            log.warn("SRS 서버에 연결할 수 없어 라이브 목록을 빈 값으로 반환합니다.", e);
            return List.of();
        }

        // 현재 송출 중인 방송이 없으면 빈 목록 반환
        if (activeStreamKeys.isEmpty()) {
            return List.of();
        }

        // SRS에서 송출 중인 streamKey와
        // DB의 방송 정보를 매칭
        List<Broadcast> broadcasts =
                broadcastRepository
                        .findByStreamKeyIn(
                                activeStreamKeys
                        );

        return broadcasts.stream()
                .map(
                        broadcast ->
                                new LiveBroadcastResponse(

                                        // 방송 ID
                                        broadcast.getId(),

                                        // 방송 제목
                                        broadcast.getTitle(),

                                        // 방송자 닉네임
                                        broadcast
                                                .getUser()
                                                .getNickname(),

                                        // 실제 SRS 송출 중이므로 LIVE
                                        "LIVE",

                                        // Redis 실제 시청자 수
                                        liveViewerService
                                                .getViewerCount(
                                                        broadcast.getId()
                                                ),

                                        // 썸네일
                                        broadcast
                                                .getThumbnailUrl(),

                                        // HLS 주소
                                        hlsBaseUrl
                                                + "/live/"
                                                + broadcast
                                                .getStreamKey()
                                                + ".m3u8",

                                        // 방송 시작 시간
                                        broadcast
                                                .getStartedAt()
                                )
                )
                .toList();
    }
}