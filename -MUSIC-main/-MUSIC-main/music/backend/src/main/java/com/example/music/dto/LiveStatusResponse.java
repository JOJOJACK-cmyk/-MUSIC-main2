package com.example.music.dto;

public record LiveStatusResponse(
        boolean srsConnected,
        boolean live,
        String status
) {
}