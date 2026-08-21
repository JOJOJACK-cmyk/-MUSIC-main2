package com.example.music.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatMessageDto {
    private String roomId;
    private String sender;
    private String message;
    private MessageType type;

    public enum MessageType {
        ENTER, TALK, LEAVE
    }
}
