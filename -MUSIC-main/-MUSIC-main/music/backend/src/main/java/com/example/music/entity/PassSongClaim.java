package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 곡 수 제한 이용권(라이트 30곡)으로 전곡 재생한 곡. 이용권 하나에 같은 곡은 한 번만 차감된다.
 * 외래키 없이 ID 만 둔다 — 곡 삭제(MusicRemover)가 이 기록에 막히지 않도록. 곡 삭제 시 함께 지운다.
 */
@Entity
@Table(
        name = "tb_pass_song",
        uniqueConstraints = @UniqueConstraint(name = "uk_tb_pass_song", columnNames = {"pass_id", "music_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PassSongClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pass_id", nullable = false)
    private Long passId;

    @Column(name = "music_id", nullable = false)
    private Long musicId;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;

    public PassSongClaim(Long passId, Long musicId) {
        this.passId = passId;
        this.musicId = musicId;
        this.claimedAt = LocalDateTime.now();
    }
}
