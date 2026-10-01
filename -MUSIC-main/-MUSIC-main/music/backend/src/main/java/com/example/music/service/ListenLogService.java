package com.example.music.service;

import com.example.music.dto.ListenLogDto;
import com.example.music.entity.ListenLog;
import com.example.music.entity.Music;
import com.example.music.entity.User;
import com.example.music.repository.ListenLogRepository;
import com.example.music.repository.MusicRepository;
import com.example.music.repository.PassRepository; // 💡 PassRepository 추가
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
    private final ChartService chartService;
    private final RecentPlayService recentPlayService;

    // 🔥 이용권(결제) 상태 검증을 위한 PassRepository 주입
    private final PassRepository passRepository;

    @Transactional
    public void recordLog(ListenLogDto dto) {
        // 1. 30초 미만 청취 차단
        if (dto.getListenSeconds() == null || dto.getListenSeconds() < 30) {
            throw new IllegalArgumentException("청취 시간이 30초 미만인 로그는 유효하지 않습니다.");
        }

        // 2. 유저 식별 (ID 또는 Email로 실제 DB 유저 조회)
        User user = null;
        if (dto.getUserId() != null) {
            user = userRepository.findById(dto.getUserId()).orElse(null);
        }
        if (user == null && dto.getEmail() != null && !dto.getEmail().isBlank()) {
            user = userRepository.findByEmail(dto.getEmail()).orElse(null);
        }

        if (user == null) {
            log.warn("[Log Collector] 인증된 사용자를 찾을 수 없어 로그 기록을 취소합니다.");
            return;
        }

        // 최근 들은 곡은 이용권과 무관하게 모든 로그인 사용자에게 기록 (차트 반영과는 별개)
        if (dto.getMusicId() != null && musicRepository.existsById(dto.getMusicId())) {
            recentPlayService.record(user.getId(), dto.getMusicId());
        }

        // 🔥 3. 이용권(결제) 상태 검증 — 관리자/부관리자는 이용권 없이도 청취 기록 인정
        boolean hasValidPass = passRepository.existsByUser_IdAndIsActiveTrueAndExpireDateAfter(
                user.getId(), LocalDateTime.now()
        );
        String role = user.getRole() == null ? "" : user.getRole().toUpperCase();
        boolean isStaff = role.equals("ROLE_ADMIN") || role.equals("ADMIN")
                || role.equals("ROLE_SUB_ADMIN") || role.equals("SUB_ADMIN");

        if (!hasValidPass && !isStaff) {
            // 무료 회원의 미리듣기는 차트에 반영하지 않는다 (에러 아님 — 조용히 스킵)
            log.debug("[Log Collector] 이용권 없는 회원 - 청취 기록/차트 반영 생략: user={}", user.getId());
            return;
        }

        // 4. 30초 쿨다운 중복 방어 (DB 조회)
        LocalDateTime thirtySecondsAgo = LocalDateTime.now().minusSeconds(30);
        boolean isDuplicate = listenLogRepository.checkRecentLogExists(
                user.getId(), dto.getMusicId(), thirtySecondsAgo
        );

        if (isDuplicate) {
            log.warn("[Log Collector] ⚠️ 30초 내 중복 청취 감지 (기록 생략) - User: {} ({}), MusicId: {}",
                    user.getId(), user.getEmail(), dto.getMusicId());
            return;
        }

        // 5. 음원 조회
        Music music = musicRepository.findById(dto.getMusicId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 음원입니다. ID: " + dto.getMusicId()));

        // 6. 로그 저장
        ListenLog logEntity = new ListenLog();
        logEntity.setUser(user);
        logEntity.setMusic(music);
        logEntity.setListenSeconds(dto.getListenSeconds());
        logEntity.setListenedAt(LocalDateTime.now());

        listenLogRepository.save(logEntity);

        // 7. 실시간 차트 점수 누적 연동 (청취가 정상 기록될 때 Redis ZSet 점수 1점 증가)
        chartService.incrementScore(dto.getMusicId(), 1.0);

        log.info("[Log Collector] ✅ 청취 로그 저장 완료 및 실시간 차트 반영 - User: {} ({}), Music: {}, Duration: {}s",
                user.getId(), user.getEmail(), music.getTitle(), dto.getListenSeconds());
    }
}