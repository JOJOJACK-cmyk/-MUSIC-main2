package com.example.music.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PaymentRequestDto {
    private Long userId;
    private String orderId;
    private String paymentKey;
    private int amount;
    private String passType;
}