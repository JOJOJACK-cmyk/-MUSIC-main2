package com.example.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 결제 승인 API 호출 클라이언트.
 * 프론트에서 결제창을 통해 발급된 paymentKey/orderId/amount 를
 * 토스 서버에 승인 요청하여 "실제 결제 완료" 여부를 검증한다.
 */
@Slf4j
@Component
public class TossPaymentClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String secretKey;
    private final String confirmUrl;

    public TossPaymentClient(
            @Value("${toss.payments.secret-key}") String secretKey,
            @Value("${toss.payments.confirm-url}") String confirmUrl
    ) {
        this.secretKey = secretKey;
        this.confirmUrl = confirmUrl;
    }

    public static class TossPaymentException extends RuntimeException {
        public TossPaymentException(String message) {
            super(message);
        }
    }

    /**
     * 결제 승인. 실패 시 {@link TossPaymentException} 을 던진다.
     *
     * @return 토스가 확정한 결제 정보 (승인된 금액, 결제수단, 주문명 등)
     */
    public TossConfirmResult confirm(String paymentKey, String orderId, long amount) {
        // 시크릿 키 뒤에 ':' 을 붙여 Base64 인코딩 → Basic 인증 (토스 규격)
        String basic = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "paymentKey", paymentKey,
                    "orderId", orderId,
                    "amount", amount
            ));
        } catch (Exception e) {
            throw new TossPaymentException("결제 요청 본문 생성 실패");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(confirmUrl))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("[Toss] 결제 승인 통신 오류", e);
            throw new TossPaymentException("결제 서버와 통신할 수 없습니다.");
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new TossPaymentException("결제 응답을 해석할 수 없습니다.");
        }

        if (response.statusCode() != 200) {
            String message = json.path("message").asText("결제 승인에 실패했습니다.");
            String code = json.path("code").asText("");
            log.warn("[Toss] 결제 승인 실패 status={} code={} message={}", response.statusCode(), code, message);
            throw new TossPaymentException(message);
        }

        String status = json.path("status").asText("");
        if (!"DONE".equals(status)) {
            throw new TossPaymentException("결제가 완료되지 않았습니다. (상태: " + status + ")");
        }

        long approvedAmount = json.path("totalAmount").asLong(
                json.path("amount").asLong(0));
        String method = json.path("method").asText("");
        String orderName = json.path("orderName").asText("");

        return new TossConfirmResult(approvedAmount, method, orderName, status);
    }

    /**
     * 결제 취소(전액 환불). 실패 시 {@link TossPaymentException}.
     * 승인 URL(.../v1/payments/confirm)에서 기본 경로를 얻어 .../v1/payments/{paymentKey}/cancel 을 호출한다.
     */
    public void cancel(String paymentKey, String reason) {
        String basic = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        String base = confirmUrl.endsWith("/confirm")
                ? confirmUrl.substring(0, confirmUrl.length() - "/confirm".length())
                : "https://api.tosspayments.com/v1/payments";

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("cancelReason", reason == null ? "고객 요청" : reason));
        } catch (Exception e) {
            throw new TossPaymentException("취소 요청 본문 생성 실패");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/" + java.net.URLEncoder.encode(paymentKey, StandardCharsets.UTF_8) + "/cancel"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("[Toss] 결제 취소 통신 오류", e);
            throw new TossPaymentException("결제 서버와 통신할 수 없습니다.");
        }
        if (response.statusCode() != 200) {
            String message = "결제 취소에 실패했습니다.";
            try {
                message = objectMapper.readTree(response.body()).path("message").asText(message);
            } catch (Exception ignore) {}
            log.warn("[Toss] 결제 취소 실패 status={} message={}", response.statusCode(), message);
            throw new TossPaymentException(message);
        }
    }

    public record TossConfirmResult(long amount, String method, String orderName, String status) {}
}
