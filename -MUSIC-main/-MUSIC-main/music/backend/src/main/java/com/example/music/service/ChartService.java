package com.example.music.service;

import com.example.music.dto.MusicRankingDto;
import com.example.music.entity.Music;
import com.example.music.repository.MusicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChartService {

    private final StringRedisTemplate redisTemplate;
    private final MusicRepository musicRepository;

    private static final String REAL_TIME_CHART_KEY = "chart:realtime";

    /**
     * 곡 청취 시 점수 누적 (30초 청취 로그 수집기에서 호출)
     */
    public void incrementScore(Long musicId, double scoreInc) {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.incrementScore(REAL_TIME_CHART_KEY, musicId.toString(), scoreInc);
    }

    /**
     * 실시간 TOP 100 차트 조회 (Redis 순위 + MySQL 음원 상세 정보 결합)
     */
    public List<MusicRankingDto> getRealTimeTop100() {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // Redis Sorted Set에서 점수가 높은 순(역순)으로 상위 100개 조회 (0위 ~ 99위)
        Set<ZSetOperations.TypedTuple<String>> typedTuples = zSetOps.reverseRangeWithScores(REAL_TIME_CHART_KEY, 0, 99);

        List<MusicRankingDto> rankingList = new ArrayList<>();
        if (typedTuples == null || typedTuples.isEmpty()) {
            return rankingList;
        }

        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            if (tuple.getValue() != null) {
                Long musicId = Long.valueOf(tuple.getValue());
                Long listenCount = tuple.getScore() != null ? tuple.getScore().longValue() : 0L;

                // DB에서 음원 정보 조회 (없을 경우 기본값 처리)
                Music music = musicRepository.findById(musicId).orElse(null);

                String title = (music != null) ? music.getTitle() : "삭제된 음원";
                String artist = (music != null) ? music.getArtist() : "알 수 없음";
                String youtubeVideoId = (music != null) ? music.getYoutubeVideoId() : "";
                String thumbnailUrl = (music != null) ? music.getThumbnailUrl() : "";

                // 회원님께서 정의하신 MusicRankingDto 레코드 규격에 맞게 매핑
                MusicRankingDto dto = new MusicRankingDto(
                        rank++,
                        musicId,
                        youtubeVideoId,
                        title,
                        artist,
                        thumbnailUrl,
                        listenCount
                );

                rankingList.add(dto);
            }
        }

        return rankingList;
    }
}