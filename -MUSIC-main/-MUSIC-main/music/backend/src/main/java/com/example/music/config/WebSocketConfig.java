package com.example.music.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // REST CORS 와 같은 출처만 웹소켓 접속 허용 (다른 사이트가 사용자 쿠키로 채팅/투표에 접속하는 것 방지)
    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}")
    private String allowedOriginsRaw;

    private String[] allowedOrigins() {
        return java.util.Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        // 기존 채팅용 WebSocket
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(allowedOrigins())
                .withSockJS();

        // 라이브 신청곡 / 투표용 WebSocket
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns(allowedOrigins())
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // 서버 → 클라이언트 구독 경로
        // 기존 채팅: /sub
        // 라이브 투표: /topic
        // /queue : 특정 사용자에게만 보내는 개인 메시지 (/user/queue/... 로 구독)
        registry.enableSimpleBroker(
                "/sub",
                "/topic",
                "/queue"
        );

        // 클라이언트 → 서버 발행 경로
        // 기존 채팅: /pub
        // 라이브 투표: /app
        registry.setApplicationDestinationPrefixes(
                "/pub",
                "/app"
        );
    }
}