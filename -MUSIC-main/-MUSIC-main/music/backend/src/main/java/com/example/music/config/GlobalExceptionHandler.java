package com.example.music.config;

import com.example.music.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Hidden
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 401 Unauthorized: 인증 실패 (로그인 안 됨, 토큰 만료 등)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException e) {
        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "UNAUTHORIZED",
                "인증에 실패하였습니다: " + e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // 403 Forbidden: 인가 실패 (권한이 없는 사용자 등)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "ACCESS_DENIED",
                "해당 리소스에 대한 접근 권한이 없습니다."
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // 404 Not Found: 존재하지 않는 URL 경로 요청 시
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NoResourceFoundException e) {
        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "NOT_FOUND",
                "존재하지 않는 API 경로입니다."
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // 400 Bad Request: 잘못된 인자나 입력값 문제 발생 시 (유튜브 링크 오류 등 차단)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "BAD_REQUEST",
                // 실제 사유를 그대로 노출한다 (영상 없음/비공개/삭제/잘못된 ID 등)
                e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage()
                        : "유효하지 않은 요청이거나 지원하지 않는 유튜브 링크입니다."
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 429 Too Many Requests: YouTube API 일일 할당량 소진
    @ExceptionHandler(com.example.music.service.YouTubeApiService.QuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuotaExceeded(
            com.example.music.service.YouTubeApiService.QuotaExceededException e) {
        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "YOUTUBE_QUOTA_EXCEEDED",
                e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage()
                        : "YouTube API 일일 할당량이 소진되었습니다. 잠시 후 다시 시도해 주세요."
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    // 400 Bad Request: 토스 결제 승인/취소 실패 (토스가 준 사유를 그대로 전달)
    @ExceptionHandler(com.example.music.service.TossPaymentClient.TossPaymentException.class)
    public ResponseEntity<ErrorResponse> handleTossPayment(com.example.music.service.TossPaymentClient.TossPaymentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("ERROR", "PAYMENT_FAILED", e.getMessage()));
    }

    // 403 Forbidden: 이용권에 포함되지 않은 기능 (프론트는 code 로 이용권 안내를 띄운다)
    @ExceptionHandler(com.example.music.service.PassEntitlementService.PassRequiredException.class)
    public ResponseEntity<ErrorResponse> handlePassRequired(com.example.music.service.PassEntitlementService.PassRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("ERROR", "PASS_REQUIRED", e.getMessage()));
    }

    // 500 Internal Server Error: 그 외 예상치 못한 모든 서버 에러 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        // 원인(SQL·테이블·제약 이름 등)은 서버 로그에만 남기고, 응답에는 추적용 ID 만 준다.
        // 문제 신고가 오면 이 ID 로 서버 로그에서 원본 스택을 찾는다.
        String errorId = java.util.UUID.randomUUID().toString().substring(0, 8);
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("[500] errorId={} {} : {}", errorId, e.getClass().getName(), e.getMessage(), e);

        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "INTERNAL_SERVER_ERROR",
                "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. (오류 ID: " + errorId + ")"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}