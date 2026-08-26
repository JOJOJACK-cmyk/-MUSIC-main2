package com.example.music.controller;

import com.example.music.dto.PaymentRequestDto;
import com.example.music.dto.PaymentResponseDto;
import com.example.music.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/request")
    public ResponseEntity<PaymentResponseDto> requestPayment(@RequestBody PaymentRequestDto requestDto) {
        // TODO: 결제 연동 로직 (Toss / STOVE Pay 등) 구현
        PaymentResponseDto response = paymentService.processPayment(requestDto);
        return ResponseEntity.ok(response);
    }
}