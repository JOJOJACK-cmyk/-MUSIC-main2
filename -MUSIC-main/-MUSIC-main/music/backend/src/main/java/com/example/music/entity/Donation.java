package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 음표(라이브 방송자 후원). 1음표 = 1원.
 * 결제 전 서버가 PENDING 으로 먼저 만들어 금액을 고정하고, 토스 승인 후 DONE 으로 바꾼다
 * (프론트가 금액을 바꿔서 승인 요청해도 서버 금액과 다르면 거절).
 */
@Entity
@Table(
        name = "donation",
        uniqueConstraints = @UniqueConstraint(name = "uk_donation_order_id", columnNames = "order_id"),
        indexes = @Index(name = "idx_donation_recipient", columnList = "recipient_id, status, paid_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Donation {

    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private User donor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    private Broadcast broadcast;

    @Column(nullable = false)
    private int amount;

    @Column(length = 100)
    private String message;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public Donation(String orderId, User donor, Broadcast broadcast, int amount, String message) {
        this.orderId = orderId;
        this.donor = donor;
        this.recipient = broadcast.getUser();
        this.broadcast = broadcast;
        this.amount = amount;
        this.message = message;
        this.status = PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markPaid(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = DONE;
        this.paidAt = LocalDateTime.now();
    }
}
