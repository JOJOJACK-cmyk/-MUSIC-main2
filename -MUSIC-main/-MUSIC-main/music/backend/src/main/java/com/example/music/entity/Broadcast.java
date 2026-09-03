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

    @Column(name = "stream_key", nullable = false, unique = true)
    private String streamKey;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    // 채널 소개글
    @Column(length = 500)
    private String description;

    // 채널 배너 이미지 URL
    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}