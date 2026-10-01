package com.example.music.service;

import com.example.music.dto.MusicDto;
import com.example.music.entity.Music;
import com.example.music.repository.MusicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 최근 들은 곡 (사용자별 최근 50곡, 중복 없이 최신순).
 * Redis LIST history:{userId}. 차트용 청취 로그(이용권 회원만 기록)와 달리 로그인한 모든 사용자에게 기록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecentPlayService {

    private static final int MAX_RECENT = 50;

    private final StringRedisTemplate redis;
    private final MusicRepository musicRepository;

    private static String key(Long userId) {
        return "history:" + userId;
    }

    public void record(Long userId, Long musicId) {
        if (userId == null || musicId == null) return;
        try {
            String k = key(userId);
            String v = String.valueOf(musicId);
            redis.opsForList().remove(k, 0, v); // 이미 있으면 빼고 맨 앞으로
            redis.opsForList().leftPush(k, v);
            redis.opsForList().trim(k, 0, MAX_RECENT - 1);
        } catch (Exception e) {
            log.warn("최근 들은 곡 기록 실패 (무시) userId={}: {}", userId, e.getMessage());
        }
    }

    public List<MusicDto.Response> getRecent(Long userId) {
        List<String> raw;
        try {
            raw = redis.opsForList().range(key(userId), 0, MAX_RECENT - 1);
        } catch (Exception e) {
            log.warn("최근 들은 곡 조회 실패 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
        if (raw == null || raw.isEmpty()) return List.of();

        List<Long> ids = new ArrayList<>();
        for (String s : raw) {
            try { ids.add(Long.valueOf(s)); } catch (NumberFormatException ignore) {}
        }
        Map<Long, Music> byId = musicRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Music::getId, Function.identity()));

        // Redis 순서(최신순) 유지, 삭제된 곡은 건너뜀
        List<MusicDto.Response> result = new ArrayList<>();
        for (Long id : ids) {
            Music m = byId.get(id);
            if (m != null) result.add(new MusicDto.Response(m));
        }
        return result;
    }
}
