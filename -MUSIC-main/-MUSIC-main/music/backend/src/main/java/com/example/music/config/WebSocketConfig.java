package com.example.music.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        // 기존 채팅용 WebSocket
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // 라이브 신청곡 / 투표용 WebSocket
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*")
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