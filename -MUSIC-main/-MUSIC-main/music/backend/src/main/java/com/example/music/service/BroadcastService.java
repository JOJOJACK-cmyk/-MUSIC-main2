package com.example.music.service;

import com.example.music.entity.Broadcast;
import com.example.music.entity.User;
import com.example.music.repository.BroadcastRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final BroadcastRepository broadcastRepository;

    /**
     * 스트림 키 발급 및 재생성
     */
    @Transactional
    public Broadcast createOrUpdateStreamKey(User user, String defaultTitle) {
        // 유저 ID로 기존 방송 정보 조회, 없으면 새로 생성
        Broadcast broadcast = broadcastRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    Broadcast newBroadcast = new Broadcast();
                    newBroadcast.setUser(user);
                    // user.getName() 대신 getNickname() 사용
                    String title = defaultTitle != null ? defaultTitle : user.getNickname() + "의 방송국";
                    newBroadcast.setTitle(title);
                    newBroadcast.setStatus("OFF");
                    newBroadcast.setCreatedAt(LocalDateTime.now());
                    return newBroadcast;
                });

        // 고유 스트림 키 생성 (UUID 기반 난수)
        String uniqueStreamKey = "live_" + UUID.randomUUID().toString().replace("-", "");
        broadcast.setStreamKey(uniqueStreamKey);

        return broadcastRepository.save(broadcast);
    }

    @Transactional
    public void updateBroadcastInfo(Long userId, String title) {
        Broadcast broadcast = broadcastRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저의 방송 정보를 찾을 수 없습니다."));
        broadcast.setTitle(title);
    }

    @Transactional
    public void updateBroadcastStatus(Long userId, String status) {
        Broadcast broadcast = broadcastRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저의 방송 정보를 찾을 수 없습니다."));

        broadcast.setStatus(status);

        if ("ON".equalsIgnoreCase(status)) {
            broadcast.setStartedAt(LocalDateTime.now());
            broadcast.setEndedAt(null);
        } else if ("OFF".equalsIgnoreCase(status)) {
            broadcast.setEndedAt(LocalDateTime.now());
        }
    }
}