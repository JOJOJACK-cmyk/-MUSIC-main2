package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // 결제한 유저 ID

    @Column(nullable = false)
    private String orderId; // 주문 고유 번호 (PG사 연동용)

    @Column(nullable = false)
    private String paymentKey; // PG사 승인 키

    @Column(nullable = false)
    private int amount; // 결제 금액

    @Column(nullable = false)
    private String passType; // 이용권 종류 (예: 1개월 이용권, 무제한 등)

    @Column(nullable = false)
    private String status; // 결제 상태 (DONE: 완료, CANCELED: 취소 등)

    @Column(nullable = false)
    private LocalDateTime paidAt; // 결제 일시

    @Builder
    public Payment(Long userId, String orderId, String paymentKey, int amount, String passType, String status, LocalDateTime paidAt) {
        this.userId = userId;
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.passType = passType;
        this.status = status;
        this.paidAt = paidAt;
    }
}
