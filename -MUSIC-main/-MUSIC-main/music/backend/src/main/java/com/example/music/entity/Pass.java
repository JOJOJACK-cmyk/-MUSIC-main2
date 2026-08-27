package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "tb_pass",
        indexes = {
                @Index(
                        name = "idx_tb_pass_user_active_expire",
                        columnList = "user_id, is_active, expire_date"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_tb_pass_user")
    )
    private User user;

    @Column(name = "pass_name", nullable = false)
    private String passName;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "expire_date", nullable = false)
    private LocalDateTime expireDate;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Builder
    public Pass(
            User user,
            String passName,
            LocalDateTime startDate,
            LocalDateTime expireDate,
            boolean isActive
    ) {
        this.user = user;
        this.passName = passName;
        this.startDate = startDate;
        this.expireDate = expireDate;
        this.isActive = isActive;
    }
}