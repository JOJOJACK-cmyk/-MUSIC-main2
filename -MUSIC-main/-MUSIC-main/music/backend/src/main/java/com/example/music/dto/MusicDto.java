package com.example.music.dto;

import com.example.music.entity.Music;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class MusicDto {

    @Schema(name = "MusicCreateRequest", description = "음원 등록 요청 DTO")
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CreateRequest {

        @Schema(description = "YouTube 동영상 고유 ID", example = "dQw4w9WgXcQ", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "YouTube 동영상 ID는 필수값입니다.")
        private String youtubeVideoId;

        @Schema(description = "음악 제목", example = "Never Gonna Give You Up", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "음악 제목은 필수값입니다.")
        private String title;

        @Schema(description = "아티스트명", example = "Rick Astley")
        private String artist;

        @Schema(description = "앨범 썸네일 이미지 URL", example = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
        private String thumbnailUrl;

        public Music toEntity() {
            return Music.builder()
                    .youtubeVideoId(youtubeVideoId)
                    .title(title)
                    .artist(artist)
                    .thumbnailUrl(thumbnailUrl)
                    .build();
        }
    }

    @Schema(name = "MusicUpdateRequest", description = "음원 정보 수정 요청 DTO")
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UpdateRequest {

        @Schema(description = "수정할 음악 제목", example = "수정된 음악 제목")
        private String title;

        @Schema(description = "수정할 아티스트명", example = "수정된 아티스트")
        private String artist;

        @Schema(description = "수정할 썸네일 URL", example = "https://example.com/new_thumb.jpg")
        private String thumbnailUrl;
    }

    @Schema(name = "MusicResponse", description = "음원 정보 응답 DTO")
    @Getter
    @AllArgsConstructor
    public static class Response {

        @Schema(description = "음원 PK ID", example = "1")
        private final Long id;

        @Schema(description = "YouTube 동영상 고유 ID", example = "dQw4w9WgXcQ")
        private final String youtubeVideoId;

        @Schema(description = "음악 제목", example = "Never Gonna Give You Up")
        private final String title;

        @Schema(description = "아티스트명", example = "Rick Astley")
        private final String artist;

        @Schema(description = "썸네일 이미지 URL", example = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
        private final String thumbnailUrl;

        public Response(Music music) {
            this.id = music.getId();
            this.youtubeVideoId = music.getYoutubeVideoId();
            this.title = music.getTitle();
            this.artist = music.getArtist();
            this.thumbnailUrl = music.getThumbnailUrl();
        }
    }
}