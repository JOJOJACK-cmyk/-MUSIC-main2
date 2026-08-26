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

    @Schema(description = "사용자 ID (숫자 PK, 소셜 로그인 시 생략 가능)", example = "1")
    private Long userId;

    @Schema(description = "사용자 이메일 (소셜 로그인 식별용)", example = "cjsrudgh98@gmail.com")
    private String email;

    @Schema(description = "음원 고유 ID", example = "10")
    @NotNull(message = "음원 ID는 필수입니다.")
    private Long musicId;

    @Schema(description = "청취 시간(초 단위, 30초 이상)", example = "30")
    @NotNull(message = "청취 시간은 필수입니다.")
    @Min(value = 30, message = "청취 기록은 최소 30초 이상이어야 인정됩니다.")
    private Integer listenSeconds;
}