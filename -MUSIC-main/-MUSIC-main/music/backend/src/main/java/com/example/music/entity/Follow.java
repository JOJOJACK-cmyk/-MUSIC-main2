package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자(follower)가 다른 사용자(following, = 방송 채널 주인)를 팔로우한 관계.
 */
@Entity
@Table(
        name = "tb_follow",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follow_follower_following",
                columnNames = {"follower_id", "following_id"}
        ),
        indexes = {
                @Index(name = "idx_follow_follower", columnList = "follower_id"),
                @Index(name = "idx_follow_following", columnList = "following_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_id", nullable = false)
    private User following;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Follow(User follower, User following) {
        this.follower = follower;
        this.following = following;
        this.createdAt = LocalDateTime.now();
    }
}
