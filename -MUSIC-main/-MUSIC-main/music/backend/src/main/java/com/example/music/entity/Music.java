package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "music")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Music {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "youtube_video_id", nullable = false, unique = true, length = 50)
    private String youtubeVideoId;

    @Column(nullable = false)
    private String title;

    private String artist;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    // 💡 장르/카테고리 필드 추가 (예: "KPOP", "JPOP", "POP" 등)
    @Column(length = 50)
    private String genre;

    // 💡 영상 재생 시간(초). 쇼츠/장편 영상 필터링 및 노출 제어용
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    // 💡 유튜브 업로드 일시. 최신순 정렬 참고용
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // 💡 유튜브 조회수. 카테고리 인기곡 정렬(2차 기준)용
    @Column(name = "view_count")
    private Long viewCount;

    // 💡 가장 최근 유튜브 인기차트 동기화에서의 순위(1위가 가장 인기). null = 현재 차트에 없음.
    //    카테고리 섹션은 "지금 유행하는 곡" = trendingRank 있는 곡만, 이 순위대로 노출한다.
    @Column(name = "trending_rank")
    private Integer trendingRank;

    // 💡 관리자가 URL 을 직접 붙여넣어 등록한 곡. true 면 getAllMusic() 의 자동 정리(삭제) 대상에서 제외한다.
    //    (자동 동기화로 들어온 곡은 null/false → 기존처럼 단곡 게이트로 정리)
    @Column(name = "manual_add")
    private Boolean manualAdd;

    @Builder
    public Music(String youtubeVideoId, String title, String artist, String thumbnailUrl,
                 String genre, Long durationSeconds, LocalDateTime publishedAt, Long viewCount,
                 Boolean manualAdd) {
        this.youtubeVideoId = youtubeVideoId;
        this.title = title;
        this.artist = artist;
        this.thumbnailUrl = thumbnailUrl;
        this.genre = genre;
        this.durationSeconds = durationSeconds;
        this.publishedAt = publishedAt;
        this.viewCount = viewCount;
        this.manualAdd = manualAdd;
    }

    public void markManualAdd() {
        this.manualAdd = true;
    }

    public void update(String title, String artist, String thumbnailUrl, String genre) {
        this.title = title;
        this.artist = artist;
        this.thumbnailUrl = thumbnailUrl;
        this.genre = genre;
    }

    public void updateDuration(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public void updatePublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void updateViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public void updateTrendingRank(Integer trendingRank) {
        this.trendingRank = trendingRank;
    }

    @OneToMany(mappedBy = "music", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ListenLog> listenLogs = new ArrayList<>();
}