package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Builder
    public Music(String youtubeVideoId, String title, String artist, String thumbnailUrl) {
        this.youtubeVideoId = youtubeVideoId;
        this.title = title;
        this.artist = artist;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void update(String title, String artist, String thumbnailUrl) {
        this.title = title;
        this.artist = artist;
        this.thumbnailUrl = thumbnailUrl;
    }
}