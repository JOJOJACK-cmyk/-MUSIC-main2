package com.example.music.repository;

import com.example.music.entity.ListenLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ListenLogRepository extends JpaRepository<ListenLog, Long> {
    // 30초 쿨다운 중복 어뷰징 방지 조회 메서드
    boolean existsByUserIdAndMusicIdAndListenedAtAfter(Long userId, Long musicId, LocalDateTime afterTime);
}