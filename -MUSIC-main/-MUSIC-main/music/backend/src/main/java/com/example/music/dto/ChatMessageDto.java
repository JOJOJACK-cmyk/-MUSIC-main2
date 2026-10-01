package com.example.music.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageDto {
    private String messageId; // 서버가 부여 (삭제 대상 지정용)
    private Long senderId;    // 서버가 부여 (로그인 사용자 PK, 게스트면 null) — 채팅 금지 대상 지정용
    private String roomId;
    private String sender;
    private String message;
    private MessageType type;
    private String timestamp; // 채팅 전송 시간 (예: "2026-08-24 10:15:30")

    public enum MessageType {
        ENTER, TALK, LEAVE, VOTE,
        NOTICE, // 시스템 안내 (채팅 금지 등)
        DELETE  // messageId 의 메시지를 지우라는 신호
    }
}