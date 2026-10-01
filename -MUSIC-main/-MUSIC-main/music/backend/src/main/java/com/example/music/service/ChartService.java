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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 실시간 TOP 100 — 최근 24시간 청취 기준.
 *  - 청취 점수는 시간대별 Redis ZSET(chart:h:{epochHour})에 쌓고, 조회 시 최근 24개 버킷을 합산한다.
 *    (예전 chart:realtime 은 한 번도 줄지 않는 누적 점수라 "실시간"이 아니었다)
 *  - 같은 사용자가 같은 곡을 반복 재생해도 한 시간에 1점만 반영 (반복 재생으로 차트 올리기 방지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    private final StringRedisTemplate redisTemplate;
    private final MusicRepository musicRepository;
    private final ListenLogRepository listenLogRepository;

    private static final String BUCKET_PREFIX = "chart:h:";
    private static final String DEDUPE_PREFIX = "chart:dedupe:";
    private static final int WINDOW_HOURS = 24;
    private static final int CHART_SIZE = 100;
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private volatile List<MusicRankingDto> cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    private static long currentHour() {
        return Instant.now().getEpochSecond() / 3600;
    }

    /**
     * 30초 청취 1건 반영. 같은 (사용자, 곡) 은 시간당 1점.
     * @return 실제로 점수가 올라갔으면 true
     */
    public boolean recordListen(Long userId, Long musicId) {
        long hour = currentHour();
        try {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(
                    DEDUPE_PREFIX + hour + ":" + userId + ":" + musicId, "1", Duration.ofHours(2));
            if (!Boolean.TRUE.equals(first)) return false;
            redisTemplate.opsForZSet().incrementScore(BUCKET_PREFIX + hour, musicId.toString(), 1.0);
            return true;
        } catch (Exception e) {
            log.warn("차트 점수 반영 실패 (무시): {}", e.getMessage());
            return false;
        }
    }

    public List<MusicRankingDto> getRealTimeTop100() {
        List<MusicRankingDto> c = cached;
        if (c != null && cachedAt.plus(CACHE_TTL).isAfter(Instant.now())) return c;
        List<MusicRankingDto> fresh = compute();
        cached = fresh;
        cachedAt = Instant.now();
        return fresh;
    }

    private List<MusicRankingDto> compute() {
        Map<Long, Long> scores = loadRedisScores();
        if (scores == null || scores.isEmpty()) {
            // Redis 장애/비어 있음 → DB 청취기록(최근 24시간) 집계
            scores = new HashMap<>();
            for (Object[] row : listenLogRepository.findMusicRankingSince(LocalDateTime.now().minusHours(WINDOW_HOURS))) {
                scores.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }

        List<Map.Entry<Long, Long>> ranked = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(CHART_SIZE)
                .toList();
        Map<Long, Music> byId = musicRepository.findAllById(ranked.stream().map(Map.Entry::getKey).toList())
                .stream().collect(Collectors.toMap(Music::getId, Function.identity()));

        List<MusicRankingDto> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        int rank = 1;
        for (Map.Entry<Long, Long> e : ranked) {
            Music m = byId.get(e.getKey());
            if (m == null) continue; // 삭제된 곡
            result.add(toDto(rank++, m, e.getValue()));
            used.add(m.getId());
        }

        // 100위까지 유튜브 조회수 상위곡으로 채우기
        if (result.size() < CHART_SIZE) {
            for (Music m : musicRepository.findTop100ByViewCountIsNotNullOrderByViewCountDesc()) {
                if (result.size() >= CHART_SIZE) break;
                if (used.contains(m.getId())) continue;
                result.add(toDto(rank++, m, m.getViewCount() != null ? m.getViewCount() : 0L));
            }
        }
        return result;
    }

    /** 최근 24시간 버킷 합산. Redis 장애면 null */
    private Map<Long, Long> loadRedisScores() {
        long hour = currentHour();
        try {
            Map<Long, Long> scores = new HashMap<>();
            for (int i = 0; i < WINDOW_HOURS; i++) {
                Set<ZSetOperations.TypedTuple<String>> tuples =
                        redisTemplate.opsForZSet().rangeWithScores(BUCKET_PREFIX + (hour - i), 0, -1);
                if (tuples == null) continue;
                for (ZSetOperations.TypedTuple<String> t : tuples) {
                    if (t.getValue() == null || t.getScore() == null) continue;
                    try {
                        scores.merge(Long.valueOf(t.getValue()), t.getScore().longValue(), Long::sum);
                    } catch (NumberFormatException ignore) {}
                }
            }
            // 창 밖으로 밀려난 오래된 버킷 정리 (24~48시간 전)
            List<String> stale = new ArrayList<>();
            for (int i = WINDOW_HOURS; i < WINDOW_HOURS * 2; i++) stale.add(BUCKET_PREFIX + (hour - i));
            redisTemplate.delete(stale);
            return scores;
        } catch (Exception e) {
            log.warn("실시간 차트 Redis 조회 실패 - DB 청취기록으로 대체: {}", e.getMessage());
            return null;
        }
    }

    /** 하위호환: 예전 폴백 메서드명 */
    public List<MusicRankingDto> getRankingFromDb(int limit) {
        List<MusicRankingDto> all = getRealTimeTop100();
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    private MusicRankingDto toDto(int rank, Music m, Long count) {
        return new MusicRankingDto(rank, m.getId(), m.getYoutubeVideoId(),
                m.getTitle(), m.getArtist(), m.getThumbnailUrl(), count);
    }
}
