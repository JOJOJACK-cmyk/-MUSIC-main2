package com.example.music.service;

import com.example.music.dto.LiveBroadcastResponse;
import com.example.music.entity.Broadcast;
import com.example.music.entity.User;
import com.example.music.repository.BroadcastRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final BroadcastRepository broadcastRepository;
    private final SrsLiveStatusService srsLiveStatusService;
    private final LiveViewerService liveViewerService;
    private final SongVoteService songVoteService;

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
     * 현재 실제 송출 중인 방송 목록 조회
     */
    @Transactional(readOnly = true)
    public List<LiveBroadcastResponse>
    getLiveBroadcasts() {

        // SRS에서 현재 실제 송출 중인 스트림 키 조회
        List<String> activeStreamKeys =
                srsLiveStatusService
                        .getActiveStreamKeys();

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
                                        "http://localhost:8081/live/"
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