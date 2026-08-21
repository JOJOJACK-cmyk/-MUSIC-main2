package com.example.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "상세 음원 정보 응답 DTO")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MusicResponseDto {

    @Schema(description = "음원 고유 ID (PK)", example = "1")
    private Long musicId;

    @Schema(description = "음악 제목", example = "Dynamite")
    private String title;

    @Schema(description = "아티스트명", example = "BTS")
    private String artist;

    @Schema(description = "앨범명", example = "BE")
    private String albumName;

    @Schema(description = "재생 시간", example = "03:19")
    private String playTime;

    @Schema(description = "스트리밍 URL / YouTube 링크", example = "https://www.youtube.com/watch?v=gdZLi9oWNZg")
    private String streamUrl;

    @Schema(description = "앨범 이미지 / 썸네일 URL", example = "https://i.ytimg.com/vi/gdZLi9oWNZg/hqdefault.jpg")
    private String albumImgUrl;
}
