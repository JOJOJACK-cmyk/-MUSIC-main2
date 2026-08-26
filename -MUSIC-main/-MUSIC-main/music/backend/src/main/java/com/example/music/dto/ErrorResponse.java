package com.example.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "공통 에러 응답 DTO")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {

    @Schema(description = "HTTP 상태 코드 또는 에러 상태", example = "400")
    private String status;

    @Schema(description = "에러 분류 코드", example = "INVALID_INPUT_VALUE")
    private String code;

    @Schema(description = "에러 상세 메시지", example = "잘못된 요청 파라미터입니다.")
    private String message;

    // 편의 팩토리 메서드 (선택 사항)
    public static ErrorResponse of(String status, String code, String message) {
        return ErrorResponse.builder()
                .status(status)
                .code(code)
                .message(message)
                .build();
    }
}
