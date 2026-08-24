package com.example.music.repository;

import com.example.music.entity.ListenLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ListenLogRepository extends JpaRepository<ListenLog, Long> {
    // 특정 기간 동안의 특정 음원 청취 로그 조회 (차트 및 통계용)
    List<ListenLog> findByMusicIdAndListenedAtBetween(Long musicId, LocalDateTime start, LocalDateTime end);
}