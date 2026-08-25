package com.example.music.service;

import com.example.music.entity.Music;
import com.example.music.repository.MusicRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class MusicSnapshotService {

    private final MusicRepository musicRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public MusicSnapshotService(
            MusicRepository musicRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.musicRepository = musicRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveAllMusicSnapshot() {

        List<Music> musics = musicRepository.findAll();

        try {
            String json = objectMapper.writeValueAsString(musics);

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
}