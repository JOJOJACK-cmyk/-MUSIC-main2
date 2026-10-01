package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/** 사용자가 직접 만드는 플레이리스트 */
@Entity
@Table(name = "playlist", indexes = @Index(name = "idx_playlist_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Playlist(User user, String name) {
        this.user = user;
        this.name = name;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void rename(String name) {
        this.name = name;
        touch();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
