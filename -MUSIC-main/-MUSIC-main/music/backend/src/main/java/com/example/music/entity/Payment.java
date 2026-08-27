package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "tb_payment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tb_payment_order_id",
                        columnNames = "order_id"
                ),
                @UniqueConstraint(
                        name = "uk_tb_payment_payment_key",
                        columnNames = "payment_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_tb_payment_user_id",
                        columnList = "user_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_tb_payment_user")
    )
    private User user;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "payment_key", nullable = false)
    private String paymentKey;

    @Column(nullable = false)
    private int amount;

    @Column(name = "pass_type", nullable = false)
    private String passType;

    @Column(nullable = false)
    private String status;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Builder
    public Payment(
            User user,
            String orderId,
            String paymentKey,
            int amount,
            String passType,
            String status,
            LocalDateTime paidAt
    ) {
        this.user = user;
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.passType = passType;
        this.status = status;
        this.paidAt = paidAt;
    }
}