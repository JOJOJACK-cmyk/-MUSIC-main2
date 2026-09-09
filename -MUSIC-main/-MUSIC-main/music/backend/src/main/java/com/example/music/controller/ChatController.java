package com.example.music.controller;

import com.example.music.dto.ChatMessageDto;
import com.example.music.dto.SongVoteDto;
import com.example.music.service.SongVoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessageSendingOperations messagingTemplate;
    private final SongVoteService songVoteService;

    // "투표1", "투표 2", "vote3" 등 → 번호 추출
    private static final Pattern VOTE_CMD =
            Pattern.compile("^\\s*(?:투표|vote)\\s*(\\d{1,2})\\s*$", Pattern.CASE_INSENSITIVE);

    @MessageMapping("/chat/message")
    public void message(ChatMessageDto message) {
        message.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        if (ChatMessageDto.MessageType.ENTER.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 입장하셨습니다.");
        } else if (ChatMessageDto.MessageType.LEAVE.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 퇴장하셨습니다.");
        } else if (ChatMessageDto.MessageType.TALK.equals(message.getType())) {
            // "투표N" 이면 채팅이 아니라 투표로 처리
            Matcher m = VOTE_CMD.matcher(message.getMessage() == null ? "" : message.getMessage());
            if (m.matches()) {
                Long broadcastId = parseLong(message.getRoomId());
                int idx = Integer.parseInt(m.group(1)) - 1; // 1-base → 0-base
                if (broadcastId != null && idx >= 0) {
                    List<SongVoteDto> poll = songVoteService.getPoll(broadcastId);
                    message.setType(ChatMessageDto.MessageType.VOTE);
                    if (idx >= poll.size()) {
                        message.setMessage("없는 번호예요 (" + (idx + 1) + "번)");
                    } else {
                        boolean ok = songVoteService.vote(broadcastId, message.getSender(), idx);
                        if (ok) {
                            messagingTemplate.convertAndSend(
                                    "/topic/broadcast/" + broadcastId + "/ranking",
                                    songVoteService.getPoll(broadcastId));
                            message.setMessage(message.getSender() + " 님이 " + (idx + 1) + "번에 투표했어요");
                        } else {
                            Integer already = songVoteService.votedIndex(broadcastId, message.getSender());
                            message.setMessage(already != null
                                    ? message.getSender() + " 님은 이미 " + (already + 1) + "번에 투표했어요"
                                    : message.getSender() + " 님은 투표할 수 없어요 (로그인 필요)");
                        }
                    }
                }
            }
        }

        log.info("[Chat] Room: {}, Sender: {}, Type: {}", message.getRoomId(), message.getSender(), message.getType());
        messagingTemplate.convertAndSend("/sub/chat/room/" + message.getRoomId(), message);
    }

    private Long parseLong(String s) {
        try { return Long.valueOf(s); } catch (Exception e) { return null; }
    }
}
