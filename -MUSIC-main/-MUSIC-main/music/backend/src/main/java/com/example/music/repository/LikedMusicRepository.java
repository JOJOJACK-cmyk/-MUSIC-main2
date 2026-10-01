package com.example.music.repository;

import com.example.music.entity.LikedMusic;
import com.example.music.entity.Music;
import com.example.music.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface LikedMusicRepository extends JpaRepository<LikedMusic, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM LikedMusic lm WHERE lm.music.id = :musicId")
    void deleteByMusicId(@Param("musicId") Long musicId);

    // 특정 유저와 음악 조합으로 좋아요 기록 조회 (토글 시 이미 눌렀는지 확인용)
    Optional<LikedMusic> findByUserAndMusic(User user, Music music);

    // 특정 유저가 좋아요를 누른 모든 음악 목록 조회 (내 보관함용)
    List<LikedMusic> findByUser(User user);

    // 특정 유저가 해당 음악에 좋아요를 눌렀는지 여부 (boolean 반환)
    boolean existsByUserAndMusic(User user, Music music);
}