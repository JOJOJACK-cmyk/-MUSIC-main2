package com.example.music.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "listen_log",
        indexes = {
                @Index(
                        name = "idx_listen_log_user_music_time",
                        columnList = "user_id, music_id, listened_at"
                )
        }
)
@Getter
@Setter
public class ListenLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "music_id", nullable = false)
    private Music music;

    @Column(name = "listen_seconds", nullable = false)
    private Integer listenSeconds;

    @Column(name = "listened_at")
    private LocalDateTime listenedAt;


}