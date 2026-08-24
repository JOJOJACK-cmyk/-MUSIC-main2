package com.example.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "YouTube API 조회 영상 메타데이터 DTO")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class YouTubeVideoDto {

    @Schema(description = "내부 데이터베이스 음원 ID (DB 캐싱 저장 후 반환 시)", example = "1")
    private Long id;

    @Schema(description = "YouTube 동영상 고유 ID", example = "dQw4w9WgXcQ")
    private String youtubeVideoId;

    @Schema(description = "동영상 / 음악 제목", example = "Never Gonna Give You Up")
    private String title;

    @Schema(description = "채널명 / 아티스트명", example = "Rick Astley")
    private String artist;

    @Schema(description = "고화질 썸네일 이미지 URL", example = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
    private String thumbnailUrl;

    @Schema(description = "재생 시간 (초 단위)", example = "213")
    private Long durationSeconds;
}