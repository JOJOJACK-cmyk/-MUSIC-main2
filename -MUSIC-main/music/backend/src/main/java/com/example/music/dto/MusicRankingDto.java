package com.example.music.dto;

public record MusicRankingDto(
        int rank,
        Long musicId,
        String youtubeVideoId,
        String title,
        String artist,
        String thumbnailUrl,
        Long listenCount
) {
}