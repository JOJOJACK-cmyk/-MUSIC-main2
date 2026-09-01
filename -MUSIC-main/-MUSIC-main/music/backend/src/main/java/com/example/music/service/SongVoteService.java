package com.example.music.service;

import com.example.music.dto.SongVoteDto;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SongVoteService {

    private final StringRedisTemplate redisTemplate;

    public SongVoteService(
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    // 방송별 곡 랭킹 Redis Key
    private String getRankingKey(
            Long broadcastId
    ) {
        return "broadcast:"
                + broadcastId
                + ":song:ranking";
    }

    // 방송별 + 사용자별 투표 기록 Redis Key
    private String getUserVoteKey(
            Long broadcastId,
            String userEmail
    ) {
        return "broadcast:"
                + broadcastId
                + ":user:"
                + userEmail
                + ":votes";
    }

    /**
     * 곡 투표
     *
     * true  = 정상 투표
     * false = 이미 투표한 곡
     */
    public boolean voteSong(
            Long broadcastId,
            String userEmail,
            String songTitle
    ) {

        String rankingKey =
                getRankingKey(broadcastId);

        String userVoteKey =
                getUserVoteKey(
                        broadcastId,
                        userEmail
                );

        /*
         * Redis SET에 곡 제목 추가
         *
         * 처음 추가:
         * add() 결과 = 1
         *
         * 이미 존재:
         * add() 결과 = 0
         */
        Long added =
                redisTemplate
                        .opsForSet()
                        .add(
                                userVoteKey,
                                songTitle
                        );

        // 이미 투표한 곡
        if (added == null || added == 0) {
            return false;
        }

        // 처음 투표한 경우에만 점수 +1
        ZSetOperations<String, String> zSetOps =
                redisTemplate.opsForZSet();

        zSetOps.incrementScore(
                rankingKey,
                songTitle,
                1
        );

        return true;
    }

    // 현재 실시간 상위 곡 제목 목록 조회
    public Set<String> getTopSongs(
            Long broadcastId,
            int limit
    ) {

        String rankingKey =
                getRankingKey(broadcastId);

        ZSetOperations<String, String> zSetOps =
                redisTemplate.opsForZSet();

        return zSetOps.reverseRange(
                rankingKey,
                0,
                limit - 1
        );
    }

    // 1위 곡 가져오기
    public String getTop1Song(
            Long broadcastId
    ) {

        Set<String> topSongs =
                getTopSongs(
                        broadcastId,
                        1
                );

        if (
                topSongs != null
                        && !topSongs.isEmpty()
        ) {
            return topSongs
                    .iterator()
                    .next();
        }

        return "재생할 신청곡이 없습니다.";
    }

    // 곡 제목 + 득표수 조회
    public List<SongVoteDto>
    getTopSongRankings(
            Long broadcastId,
            int limit
    ) {

        String rankingKey =
                getRankingKey(broadcastId);

        ZSetOperations<String, String> zSetOps =
                redisTemplate.opsForZSet();

        Set<
                ZSetOperations.TypedTuple<String>
                > tuples =
                zSetOps
                        .reverseRangeWithScores(
                                rankingKey,
                                0,
                                limit - 1
                        );

        List<SongVoteDto> rankingList =
                new ArrayList<>();

        if (tuples != null) {

            for (
                    ZSetOperations.TypedTuple<String> tuple
                    : tuples
            ) {

                String songTitle =
                        tuple.getValue();

                long voteCount =
                        tuple.getScore() != null
                                ? tuple
                                .getScore()
                                .longValue()
                                : 0L;

                rankingList.add(
                        new SongVoteDto(
                                songTitle,
                                voteCount
                        )
                );
            }
        }

        return rankingList;
    }

    // 방송 종료 시 해당 방송의 신청곡 / 투표 기록 전체 삭제
    public void clearBroadcastVotes(Long broadcastId) {

        // 1. 곡 랭킹 삭제
        String rankingKey =
                getRankingKey(broadcastId);

        redisTemplate.delete(rankingKey);

        // 2. 사용자별 투표 기록 삭제
        String userVotePattern =
                "broadcast:"
                        + broadcastId
                        + ":user:*:votes";

        Set<String> userVoteKeys =
                redisTemplate.keys(userVotePattern);

        if (userVoteKeys != null &&
                !userVoteKeys.isEmpty()) {

            redisTemplate.delete(userVoteKeys);
        }

        System.out.println(
                "방송 투표 Redis 정리 완료: broadcastId="
                        + broadcastId
        );
    }
}