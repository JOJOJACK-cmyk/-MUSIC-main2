package com.example.music.repository;

import com.example.music.entity.Music;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MusicRepository extends JpaRepository<Music, Long> {
    // 유튜브 Data API DB 캐시 조회를 위한 메서드
    Optional<Music> findByYoutubeVideoId(String youtubeVideoId);
}