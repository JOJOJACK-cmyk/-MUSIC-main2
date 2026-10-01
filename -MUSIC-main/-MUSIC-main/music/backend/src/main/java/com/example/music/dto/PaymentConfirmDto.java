package com.example.music.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 토스 결제창 성공 후 프론트가 전달하는 승인 요청 값.
 */
@Getter @Setter
public class PaymentConfirmDto {
    private String paymentKey;
    private String orderId;
    private long amount;
    // 프론트에서 고른 요금제 식별자 (light / standard / premium / premium_annual — PricingPlan)
    private String planId;
}
