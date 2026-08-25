package com.example.music.controller;

import com.example.music.dto.ChatMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessageSendingOperations messagingTemplate;

    @MessageMapping("/chat/message")
    public void message(ChatMessageDto message) {
        // DTO에 정의된 포맷("yyyy-MM-dd HH:mm:ss")에 맞춰 타임스탬프 세팅
        message.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 입장 / 퇴장 안내 메시지 처리
        if (ChatMessageDto.MessageType.ENTER.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 입장하셨습니다.");
        } else if (ChatMessageDto.MessageType.LEAVE.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 퇴장하셨습니다.");
        }

        log.info("[Chat] Room: {}, Sender: {}, Message: {}",
                message.getRoomId(), message.getSender(), message.getMessage());

        // /sub/chat/room/{roomId} 로 구독자들에게 브로드캐스트
        messagingTemplate.convertAndSend("/sub/chat/room/" + message.getRoomId(), message);
    }
}