package com.example.music.service;

import com.example.music.dto.MusicRankingDto;
import com.example.music.entity.Music;
import com.example.music.repository.ListenLogRepository;
import com.example.music.repository.MusicRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class MusicSnapshotService {

    private final MusicRepository musicRepository;
    private final ListenLogRepository listenLogRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public MusicSnapshotService(
            MusicRepository musicRepository,
            ListenLogRepository listenLogRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.musicRepository = musicRepository;
        this.listenLogRepository = listenLogRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // 전체 음악 스냅샷
    public void saveAllMusicSnapshot() {

        List<Music> musics = musicRepository.findAll();

        List<MusicSnapshotItem> snapshot = musics.stream()
                .map(music -> new MusicSnapshotItem(
                        music.getId(),
                        music.getYoutubeVideoId(),
                        music.getTitle(),
                        music.getArtist(),
                        music.getThumbnailUrl()
                ))
                .toList();

        try {
            String json = objectMapper.writeValueAsString(snapshot);

            redisTemplate.opsForValue().set(
                    "music:snapshot:all",
                    json
            );

        } catch (Exception e) {
            throw new RuntimeException("음악 Redis 스냅샷 저장 실패", e);
        }
    }

    public String getAllMusicSnapshot() {
        return redisTemplate.opsForValue().get(
                "music:snapshot:all"
        );
    }


    // TOP100 저장
    public void saveTop100Ranking() {

        List<Object[]> rankingResult =
                listenLogRepository.findMusicRanking();

        List<MusicRankingDto> rankingList = new ArrayList<>();

        int rank = 1;

        for (Object[] row : rankingResult) {

            if (rank > 100) {
                break;
            }

            Long musicId = (Long) row[0];
            Long listenCount = (Long) row[1];

            Music music = musicRepository.findById(musicId)
                    .orElse(null);

            if (music == null) {
                continue;
            }

            MusicRankingDto dto = new MusicRankingDto(
                    rank,
                    music.getId(),
                    music.getYoutubeVideoId(),
                    music.getTitle(),
                    music.getArtist(),
                    music.getThumbnailUrl(),
                    listenCount
            );

            rankingList.add(dto);

            rank++;
        }

        try {
            String json =
                    objectMapper.writeValueAsString(rankingList);

            redisTemplate.opsForValue().set(
                    "music:ranking:top100",
                    json
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "TOP100 Redis 저장 실패",
                    e
            );
        }
    }

    public String getTop100Ranking() {
        return redisTemplate.opsForValue().get(
                "music:ranking:top100"
        );
    }


    private record MusicSnapshotItem(
            Long id,
            String youtubeVideoId,
            String title,
            String artist,
            String thumbnailUrl
    ) {
    }
}