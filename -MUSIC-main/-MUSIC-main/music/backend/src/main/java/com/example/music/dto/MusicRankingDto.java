package com.example.music.dto;

/**
 * 청취 기록 기반 실시간 랭킹 1건.
 * id / musicId 는 같은 값(음원 PK)이며, 프론트 플레이어가 track.id 를 참조하므로 둘 다 노출한다.
 */
public record MusicRankingDto(
        int rank,
        Long id,
        Long musicId,
        String youtubeVideoId,
        String title,
        String artist,
        String thumbnailUrl,
        Long listenCount
) {
    public MusicRankingDto(int rank, Long musicId, String youtubeVideoId,
                           String title, String artist, String thumbnailUrl, Long listenCount) {
        this(rank, musicId, musicId, youtubeVideoId, title, artist, thumbnailUrl, listenCount);
    }
}
