package com.example.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "30초 청취 로그 수집 요청 DTO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListenLogDto {

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "음원 고유 ID", example = "10")
    private Long musicId;

    @Schema(description = "청취 시간(초 단위, 30초 이상)", example = "30")
    private Integer listenSeconds;
}
