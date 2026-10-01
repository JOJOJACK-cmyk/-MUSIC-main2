package com.example.music.controller;

import com.example.music.dto.ChatMessageDto;
import com.example.music.dto.SongVoteDto;
import com.example.music.entity.User;
import com.example.music.security.AuthenticatedUserResolver;
import com.example.music.service.BroadcastService;
import com.example.music.service.PassEntitlementService;
import com.example.music.service.PassFeature;
import com.example.music.service.SongVoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessageSendingOperations messagingTemplate;
    private final SongVoteService songVoteService;
    private final BroadcastService broadcastService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final com.example.music.service.ChatModerationService chatModerationService;
    private final PassEntitlementService entitlementService;

    // "투표1", "투표 2", "vote3" 등 → 번호 추출
    private static final Pattern VOTE_CMD =
            Pattern.compile("^\\s*(?:투표|vote)\\s*(\\d{1,2})\\s*$", Pattern.CASE_INSENSITIVE);

    // "!투표창", "투표창열기", "투표창 열기", "poll open" 등 → 방송자가 신청곡 투표창을 켠다
    private static final Pattern POLL_OPEN_CMD =
            Pattern.compile("^\\s*!?\\s*(?:투표창\\s*(?:열기|오픈|open)?|poll\\s*open)\\s*$", Pattern.CASE_INSENSITIVE);

    // "!투표창닫기", "투표창 닫기", "poll close" 등 → 방송자가 신청곡 투표창을 끈다
    private static final Pattern POLL_CLOSE_CMD =
            Pattern.compile("^\\s*!?\\s*(?:투표창\\s*(?:닫기|끄기|close)|poll\\s*close)\\s*$", Pattern.CASE_INSENSITIVE);

    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final String GUEST_NAME = "게스트";

    /** 투표 1인 1표 식별자 — 닉네임은 바뀔 수 있으니 사용자 PK 로 고정 (버튼 투표와 공유) */
    public static String voterId(User user) {
        return "u:" + user.getId();
    }

    // 도배 방지: 웹소켓 연결(세션)당 5초에 5개까지
    private static final long FLOOD_WINDOW_MS = 5_000;
    private static final int FLOOD_MAX_MESSAGES = 5;
    private record FloodWindow(long startedAt, int count) {}
    private final java.util.concurrent.ConcurrentHashMap<String, FloodWindow> floodWindows = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long lastFloodSweep = System.currentTimeMillis();

    /** 이번 메시지를 허용하면 true */
    private boolean allowBySessionRate(String sessionId) {
        if (sessionId == null) return true;
        long now = System.currentTimeMillis();
        if (now - lastFloodSweep > 60_000) {
            lastFloodSweep = now;
            floodWindows.entrySet().removeIf(e -> now - e.getValue().startedAt() > FLOOD_WINDOW_MS);
        }
        FloodWindow w = floodWindows.compute(sessionId, (k, cur) ->
                (cur == null || now - cur.startedAt() > FLOOD_WINDOW_MS)
                        ? new FloodWindow(now, 1)
                        : new FloodWindow(cur.startedAt(), cur.count() + 1));
        return w.count() <= FLOOD_MAX_MESSAGES;
    }

    @MessageMapping("/chat/message")
    public void message(ChatMessageDto message, Principal principal,
                        @org.springframework.messaging.handler.annotation.Header(name = "simpSessionId", required = false) String sessionId) {
        if (message == null || message.getRoomId() == null) return;

        if (!allowBySessionRate(sessionId)) {
            if (principal != null) {
                messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/chat-notice",
                        ChatMessageDto.builder()
                                .roomId(message.getRoomId())
                                .type(ChatMessageDto.MessageType.NOTICE)
                                .message("채팅을 너무 빠르게 보내고 있어요. 잠시 후 다시 보내 주세요.")
                                .messageId(UUID.randomUUID().toString())
                                .build());
            }
            return;
        }

        // 발신자는 클라이언트가 보낸 값이 아니라 웹소켓 인증 정보(Principal)로 정한다.
        // (클라이언트 sender 를 믿으면 방송자 사칭 → "!투표창", 이름 바꿔가며 무제한 투표가 가능해짐)
        Optional<User> me = authenticatedUserResolver.resolveOptionalUser(principal);
        message.setSender(me.map(AuthenticatedUserResolver::displayName).orElse(GUEST_NAME));
        message.setSenderId(me.map(User::getId).orElse(null));
        // 프리미엄 ♪ 배지 — 클라이언트가 보낸 값은 무시하고 이용권으로 서버가 정한다
        message.setPremium(me.isPresent() && entitlementService.has(me.get(), PassFeature.CHAT_BADGE));
        message.setMessageId(UUID.randomUUID().toString());
        message.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        if (ChatMessageDto.MessageType.ENTER.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 입장하셨습니다.");
        } else if (ChatMessageDto.MessageType.LEAVE.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 퇴장하셨습니다.");
        } else if (ChatMessageDto.MessageType.TALK.equals(message.getType())) {
            String text = message.getMessage() == null ? "" : message.getMessage().trim();
            if (text.isEmpty()) return;

            Long roomBroadcastId = parseLong(message.getRoomId());
            if (me.isPresent()) {
                long remain = chatModerationService.remainingMuteSeconds(roomBroadcastId, me.get().getId());
                if (remain > 0) {
                    // 금지된 사용자에게만 안내하고 메시지는 방에 뿌리지 않는다
                    ChatMessageDto notice = ChatMessageDto.builder()
                            .roomId(message.getRoomId())
                            .type(ChatMessageDto.MessageType.NOTICE)
                            .message("채팅이 금지되어 있어요 (" + formatRemaining(remain) + " 남음)")
                            .timestamp(message.getTimestamp())
                            .messageId(UUID.randomUUID().toString())
                            .build();
                    messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/chat-notice", notice);
                    return;
                }
            }
            if (text.length() > MAX_MESSAGE_LENGTH) text = text.substring(0, MAX_MESSAGE_LENGTH);
            message.setMessage(text);

            Long broadcastId = parseLong(message.getRoomId());

            if (POLL_OPEN_CMD.matcher(text).matches()) {
                // "!투표창" 이면 방송자만 신청곡 투표창을 켤 수 있다
                boolean ok = me.isPresent()
                        && broadcastService.enableSongRequestByChatCommand(broadcastId, me.get().getId());
                message.setType(ChatMessageDto.MessageType.VOTE);
                message.setMessage(ok
                        ? message.getSender() + " 님이 투표창을 열었어요"
                        : "방송자만 투표창을 열 수 있어요");
            } else if (POLL_CLOSE_CMD.matcher(text).matches()) {
                // "!투표창닫기" 이면 방송자만 신청곡 투표창을 끌 수 있다
                boolean ok = me.isPresent()
                        && broadcastService.disableSongRequestByChatCommand(broadcastId, me.get().getId());
                message.setType(ChatMessageDto.MessageType.VOTE);
                message.setMessage(ok
                        ? message.getSender() + " 님이 투표창을 닫았어요"
                        : "방송자만 투표창을 닫을 수 있어요");
            } else {
                // "투표N" 이면 채팅이 아니라 투표로 처리
                Matcher m = VOTE_CMD.matcher(text);
                if (m.matches() && broadcastId != null) {
                    int idx = Integer.parseInt(m.group(1)) - 1; // 1-base → 0-base
                    if (idx >= 0) {
                        List<SongVoteDto> poll = songVoteService.getPoll(broadcastId);
                        message.setType(ChatMessageDto.MessageType.VOTE);
                        if (idx >= poll.size()) {
                            message.setMessage("없는 번호예요 (" + (idx + 1) + "번)");
                        } else if (me.isEmpty()) {
                            message.setMessage("로그인 후 투표할 수 있어요");
                        } else {
                            String voter = voterId(me.get());
                            boolean ok = songVoteService.vote(broadcastId, voter, idx);
                            if (ok) {
                                messagingTemplate.convertAndSend(
                                        "/topic/broadcast/" + broadcastId + "/ranking",
                                        songVoteService.getPoll(broadcastId));
                                message.setMessage(message.getSender() + " 님이 " + (idx + 1) + "번에 투표했어요");
                            } else {
                                Integer already = songVoteService.votedIndex(broadcastId, voter);
                                message.setMessage(already != null
                                        ? message.getSender() + " 님은 이미 " + (already + 1) + "번에 투표했어요"
                                        : message.getSender() + " 님은 투표할 수 없어요");
                            }
                        }
                    }
                }
            }
        } else {
            // VOTE 등 시스템 메시지 타입은 클라이언트가 직접 보낼 수 없다
            return;
        }

        log.info("[Chat] Room: {}, Sender: {}, Type: {}", message.getRoomId(), message.getSender(), message.getType());
        messagingTemplate.convertAndSend("/sub/chat/room/" + message.getRoomId(), message);
    }

    private static String formatRemaining(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "시간 " + ((seconds % 3600) / 60) + "분";
        if (seconds >= 60) return (seconds / 60) + "분";
        return seconds + "초";
    }

    private Long parseLong(String s) {
        try { return Long.valueOf(s); } catch (Exception e) { return null; }
    }
}
