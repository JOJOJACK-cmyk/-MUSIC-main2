package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_pass")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // 이용권을 보유한 유저 ID

    @Column(nullable = false)
    private String passName; // 이용권 이름 (예: 스트리밍 무제한 1개월권)

    @Column(nullable = false)
    private LocalDateTime startDate; // 이용권 시작일

    @Column(nullable = false)
    private LocalDateTime expireDate; // 이용권 만료일

    @Column(nullable = false)
    private boolean isActive; // 활성화 여부 (true: 사용 가능, false: 만료/정지)

    @Builder
    public Pass(Long userId, String passName, LocalDateTime startDate, LocalDateTime expireDate, boolean isActive) {
        this.userId = userId;
        this.passName = passName;
        this.startDate = startDate;
        this.expireDate = expireDate;
        this.isActive = isActive;
    }
}