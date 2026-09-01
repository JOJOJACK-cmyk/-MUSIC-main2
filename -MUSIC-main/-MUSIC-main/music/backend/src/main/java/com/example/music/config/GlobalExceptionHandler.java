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

    // [신규] 401 Unauthorized: 인증 실패 (로그인 안 됨, 토큰 만료 등)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException e) {
        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "UNAUTHORIZED",
                "인증에 실패하였습니다: " + e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // [신규] 403 Forbidden: 인가 실패 (권한이 없는 사용자, 청취/스트리밍 권한 부족 등)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "ACCESS_DENIED",
                "해당 리소스에 대한 접근 권한이 없습니다."
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // 1. 존재하지 않는 URL 경로 요청 시 (404 Not Found)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NoResourceFoundException e) {
        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "NOT_FOUND",
                "존재하지 않는 API 경로입니다."
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // 2. 잘못된 인자나 입력값 문제 발생 시 (400 Bad Request)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "BAD_REQUEST",
                e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 3. 그 외 예상치 못한 모든 서버 에러 처리 (500 Internal Server Error)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        e.printStackTrace();

        ErrorResponse response = new ErrorResponse(
                "ERROR",
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다. 관리자에게 문의하세요."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}