package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 스토어 주문 (상품 1종 × 수량). 결제 전 서버가 PENDING 으로 만들어 금액을 고정한다.
 * 상태: PENDING → PAID → SHIPPING → DELIVERED,  PAID/SHIPPING → CANCELLED(환불)
 */
@Entity
@Table(
        name = "shop_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_shop_order_order_id", columnNames = "order_id"),
        indexes = {
                @Index(name = "idx_shop_order_user", columnList = "user_id, created_at"),
                @Index(name = "idx_shop_order_status", columnList = "status, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopOrder {

    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String SHIPPING = "SHIPPING";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    /** 관리자가 바꿀 수 있는 상태 전이 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            PAID, Set.of(SHIPPING, CANCELLED),
            SHIPPING, Set.of(DELIVERED, CANCELLED)
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 주문 당시 상품명/가격 (상품 정보가 나중에 바뀌어도 주문 내역은 그대로)
    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    // 이용권 스토어 할인율(%) — 주문을 만들 때 확정. 할인 없으면 0 (요금제를 나누기 전 주문은 null)
    @Column(name = "discount_pct")
    private Integer discountPct;

    @Column(name = "recipient_name", nullable = false, length = 30)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String zipcode;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(name = "address_detail", length = 100)
    private String addressDetail;

    @Column(length = 100)
    private String memo;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public ShopOrder(String orderId, User user, Product product, int quantity,
                     String recipientName, String phone, String zipcode, String address,
                     String addressDetail, String memo, int discountPct) {
        this.orderId = orderId;
        this.user = user;
        this.product = product;
        this.productName = product.getName();
        this.unitPrice = product.getPrice();
        this.quantity = quantity;
        this.discountPct = Math.max(0, Math.min(discountPct, 50));
        int list = product.getPrice() * quantity;
        // 원 단위 버림 (10% 할인 18,000원 → 16,200원)
        this.totalAmount = list - (list * this.discountPct / 100);
        this.recipientName = recipientName;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.memo = memo;
        this.status = PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markPaid(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = PAID;
        this.paidAt = LocalDateTime.now();
    }

    public boolean canMoveTo(String next) {
        return TRANSITIONS.getOrDefault(status, Set.of()).contains(next);
    }

    public void moveTo(String next) {
        if (!canMoveTo(next)) {
            throw new IllegalArgumentException("'" + status + "' 상태에서 '" + next + "' 로 바꿀 수 없습니다.");
        }
        this.status = next;
        if (CANCELLED.equals(next)) this.cancelledAt = LocalDateTime.now();
    }
}
