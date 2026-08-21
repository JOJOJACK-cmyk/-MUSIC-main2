package com.example.music.repository;

import com.example.music.entity.Music;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MusicRepository extends JpaRepository<Music, Long> {
    Optional<Music> findByYoutubeVideoId(String youtubeVideoId);
    List<Music> findByTitleContainingIgnoreCaseOrArtistContainingIgnoreCase(String title, String artist);
}