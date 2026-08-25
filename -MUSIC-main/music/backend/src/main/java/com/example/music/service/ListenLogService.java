package com.example.music.service;

import com.example.music.dto.ListenLogDto;
import com.example.music.entity.ListenLog;
import com.example.music.entity.Music;
import com.example.music.entity.User;
import com.example.music.repository.ListenLogRepository;
import com.example.music.repository.MusicRepository;
import com.example.music.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListenLogService {

    private final ListenLogRepository listenLogRepository;
    private final UserRepository userRepository;
    private final MusicRepository musicRepository;

    @Transactional
    public void recordLog(ListenLogDto dto) {
        // 1. 유효성 검사 (30초 미만 트리거 차단)
        if (dto.getListenSeconds() == null || dto.getListenSeconds() < 30) {
            throw new IllegalArgumentException("청취 시간이 30초 미만인 로그는 유효하지 않습니다.");
        }

        // 2. 30초 쿨다운 검증 (동일 사용자의 비정상적인 반복 호출 어뷰징 방지)
        LocalDateTime thirtySecondsAgo = LocalDateTime.now().minusSeconds(30);
        boolean isDuplicate = listenLogRepository.existsByUserIdAndMusicIdAndListenedAtAfter(
                dto.getUserId(), dto.getMusicId(), thirtySecondsAgo
        );

        if (isDuplicate) {
            log.warn("[Log Collector] 중복 청취 로그 감지 - User: {}, Music: {}", dto.getUserId(), dto.getMusicId());
            return;
        }

        // 3. 연관 엔티티 조회
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + dto.getUserId()));
        Music music = musicRepository.findById(dto.getMusicId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 음원입니다. ID: " + dto.getMusicId()));

        // 4. 청취 로그 엔티티 생성 및 저장
        ListenLog logEntity = new ListenLog();
        logEntity.setUser(user);
        logEntity.setMusic(music);
        logEntity.setListenSeconds(dto.getListenSeconds());
        logEntity.setListenedAt(LocalDateTime.now());

        listenLogRepository.save(logEntity);

        log.info("[Log Collector] 청취 로그 수집 완료 - User: {}, Music: {}, Duration: {} seconds",
                user.getId(), music.getTitle(), dto.getListenSeconds());
    }
}