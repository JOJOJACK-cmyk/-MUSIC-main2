package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 플레이리스트에 담긴 곡.
 * 곡(Music)이 관리자 삭제/카탈로그 정리로 지워지면 DB 의 ON DELETE CASCADE 로 함께 지워진다
 * (기존 삭제 코드들이 이 테이블을 몰라도 FK 오류가 나지 않게).
 */
@Entity
@Table(
        name = "playlist_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_playlist_music", columnNames = {"playlist_id", "music_id"}),
        indexes = @Index(name = "idx_playlist_item_order", columnList = "playlist_id, position")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "music_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Music music;

    @Column(nullable = false)
    private int position;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    public PlaylistItem(Playlist playlist, Music music, int position) {
        this.playlist = playlist;
        this.music = music;
        this.position = position;
        this.addedAt = LocalDateTime.now();
    }
}
