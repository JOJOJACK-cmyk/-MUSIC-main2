package com.example.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Schema(description = "30초 청취 로그 수집 요청 DTO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListenLogDto {

    @Schema(description = "사용자 ID", example = "1")
    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    @Schema(description = "음원 고유 ID", example = "10")
    @NotNull(message = "음원 ID는 필수입니다.")
    private Long musicId;

    @Schema(description = "청취 시간(초 단위, 30초 이상)", example = "30")
    @NotNull(message = "청취 시간은 필수입니다.")
    @Min(value = 30, message = "청취 기록은 최소 30초 이상이어야 인정됩니다.")
    private Integer listenSeconds;
}
