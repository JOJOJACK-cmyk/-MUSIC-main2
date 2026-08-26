package com.example.music.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
public class PaymentResponseDto {
    private String orderId;
    private String status;
    private String message;
}