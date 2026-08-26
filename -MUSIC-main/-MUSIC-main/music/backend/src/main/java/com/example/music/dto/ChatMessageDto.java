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
    private String roomId;
    private String sender;
    private String message;
    private MessageType type;
    private String timestamp; // 채팅 전송 시간 (예: "2026-08-24 10:15:30")

    public enum MessageType {
        ENTER, TALK, LEAVE
    }
}