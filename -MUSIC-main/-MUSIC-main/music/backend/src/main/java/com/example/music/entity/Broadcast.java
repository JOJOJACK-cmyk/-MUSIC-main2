package com.example.music.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "broadcast")
@Getter
@Setter
public class Broadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    // 송출용 비밀 키. OBS 에는 "{playbackId}?key={streamKey}" 형태로 넣는다 (obsStreamKey()).
    // 공개 HLS 주소에는 절대 노출하지 않는다.
    @Column(name = "stream_key", nullable = false, unique = true)
    private String streamKey;

    // 공개 재생 ID. SRS 스트림 이름 = HLS/썸네일 파일명 (/live/{playbackId}.m3u8)
    @Column(name = "playback_id", unique = true, length = 40)
    private String playbackId;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    // 채널 소개글
    @Column(length = 500)
    private String description;

    // 채널 배너 이미지 URL
    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    // 콘텐츠 카테고리 (음악 / 함께듣기 / 토크 / 신곡소개 / 기타)
    @Column(length = 30)
    private String category;

    // 신청곡 & 실시간 투표 사용 여부 (방송자가 채널 설정에서 켜야 시청 페이지에 노출)
    @Column(name = "song_request_enabled", nullable = false)
    private boolean songRequestEnabled = false;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static String newPlaybackId() {
        return "pb_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    /** OBS "스트림 키" 칸에 넣을 값. SRS 는 '?' 앞을 스트림 이름, 뒤를 param 으로 넘겨준다. */
    public String obsStreamKey() {
        return playbackId + "?key=" + streamKey;
    }
}