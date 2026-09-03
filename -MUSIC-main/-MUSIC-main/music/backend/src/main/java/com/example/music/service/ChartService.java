package com.example.music.service;

import com.example.music.dto.MusicRankingDto;
import com.example.music.entity.Music;
import com.example.music.repository.ListenLogRepository;
import com.example.music.repository.MusicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    private final StringRedisTemplate redisTemplate;
    private final MusicRepository musicRepository;
    private final ListenLogRepository listenLogRepository;

    private static final String REAL_TIME_CHART_KEY = "chart:realtime";
    private static final int CHART_SIZE = 100;

    /** 곡 청취 시 점수 누적 (30초 청취 로그 수집기에서 호출) */
    public void incrementScore(Long musicId, double scoreInc) {
        redisTemplate.opsForZSet().incrementScore(REAL_TIME_CHART_KEY, musicId.toString(), scoreInc);
    }

    /**
     * 실시간 TOP 100.
     *  - 상단: 청취 기록 순위 (Redis 실시간 점수 → 없으면 DB 청취로그 집계)
     *  - 하단: 아직 아무도 안 들은 곡은 유튜브 조회수 높은 순으로 100위까지 채움
     * 청취가 쌓일수록 실제 청취 랭킹이 위로 올라온다.
     */
    public List<MusicRankingDto> getRealTimeTop100() {
        // musicId(String) -> 청취수
        LinkedHashMap<Long, Long> listenRanked = new LinkedHashMap<>();

        Set<ZSetOperations.TypedTuple<String>> tuples = null;
        try {
            tuples = redisTemplate.opsForZSet().reverseRangeWithScores(REAL_TIME_CHART_KEY, 0, CHART_SIZE - 1);
        } catch (Exception e) {
            log.warn("실시간 차트 Redis 조회 실패 - DB 청취기록으로 대체: {}", e.getMessage());
        }

        if (tuples != null && !tuples.isEmpty()) {
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                if (t.getValue() == null) continue;
                long score = t.getScore() != null ? t.getScore().longValue() : 0L;
                listenRanked.put(Long.valueOf(t.getValue()), score);
            }
        } else {
            for (Object[] row : listenLogRepository.findMusicRanking()) {
                if (listenRanked.size() >= CHART_SIZE) break;
                listenRanked.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }

        List<MusicRankingDto> result = new ArrayList<>();
        java.util.Set<Long> used = new java.util.HashSet<>();
        int rank = 1;

        for (Map.Entry<Long, Long> e : listenRanked.entrySet()) {
            Music m = musicRepository.findById(e.getKey()).orElse(null);
            if (m == null) continue;
            result.add(toDto(rank++, m, e.getValue()));
            used.add(m.getId());
        }

        // 100위까지 조회수 상위곡으로 채우기
        if (result.size() < CHART_SIZE) {
            for (Music m : musicRepository.findTop100ByViewCountIsNotNullOrderByViewCountDesc()) {
                if (result.size() >= CHART_SIZE) break;
                if (used.contains(m.getId())) continue;
                result.add(toDto(rank++, m, m.getViewCount() != null ? m.getViewCount() : 0L));
            }
        }
        return result;
    }

    /** 하위호환: 예전 폴백 메서드명 (일부 코드에서 참조 가능성) */
    public List<MusicRankingDto> getRankingFromDb(int limit) {
        List<MusicRankingDto> all = getRealTimeTop100();
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    private MusicRankingDto toDto(int rank, Music m, Long count) {
        return new MusicRankingDto(rank, m.getId(), m.getYoutubeVideoId(),
                m.getTitle(), m.getArtist(), m.getThumbnailUrl(), count);
    }
}
