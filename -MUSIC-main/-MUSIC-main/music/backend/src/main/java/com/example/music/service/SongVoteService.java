package com.example.music.service;

import com.example.music.dto.SongVoteDto; // DTO 임포트 경로에 맞게 확인해주세요!
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SongVoteService {

    private final StringRedisTemplate redisTemplate;
    private static final String RANKING_KEY = "broadcast:song:ranking";

    public SongVoteService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 투표하기 (좋아요 누를 때마다 점수 1점씩 증가)
    public void voteSong(String songTitle) {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.incrementScore(RANKING_KEY, songTitle, 1);
    }

    // 현재 실시간 상위 곡 제목 목록 조회 (예: TOP 3)
    public Set<String> getTopSongs(int limit) {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        // 점수가 높은 순(Rev)으로 0부터 limit-1까지 조회
        return zSetOps.reverseRange(RANKING_KEY, 0, limit - 1);
    }

    // 1위 곡 가져오기 (다음 곡 선정용)
    public String getTop1Song() {
        Set<String> topSongs = getTopSongs(1);
        if (topSongs != null && !topSongs.isEmpty()) {
            return topSongs.iterator().next();
        }
        return "재생할 신청곡이 없습니다.";
    }

    /**
     * 🌟 [추가됨] 곡 제목과 득표수(Score)를 함께 담은 리스트 조회 (프론트엔드 순위 렌더링용)
     */
    public List<SongVoteDto> getTopSongRankings(int limit) {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // 점수가 높은 순으로 득표수(Score)와 함께 범위 조회
        Set<ZSetOperations.TypedTuple<String>> tuples = zSetOps.reverseRangeWithScores(RANKING_KEY, 0, limit - 1);

        List<SongVoteDto> rankingList = new ArrayList<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String songTitle = tuple.getValue();
                long voteCount = tuple.getScore() != null ? tuple.getScore().longValue() : 0L;

                rankingList.add(new SongVoteDto(songTitle, voteCount));
            }
        }
        return rankingList;
    }
}