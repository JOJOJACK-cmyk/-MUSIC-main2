package com.example.music.repository;

import com.example.music.entity.ListenLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ListenLogRepository extends JpaRepository<ListenLog, Long> {

    // 1. Spring Data JPA 기본 메서드
    boolean existsByUserIdAndMusicIdAndListenedAtAfter(
            Long userId,
            Long musicId,
            LocalDateTime afterTime
    );

    // 2. 명시적 JPQL 검증 (연관관계 탐색 오류 방지용)
    @Query("""
            SELECT COUNT(l) > 0
            FROM ListenLog l
            WHERE l.user.id = :userId
              AND l.music.id = :musicId
              AND l.listenedAt >= :afterTime
            """)
    boolean checkRecentLogExists(
            @Param("userId") Long userId,
            @Param("musicId") Long musicId,
            @Param("afterTime") LocalDateTime afterTime
    );

    // 음악별 청취 횟수 집계
    @Query("""
            SELECT l.music.id, COUNT(l)
            FROM ListenLog l
            GROUP BY l.music.id
            ORDER BY COUNT(l) DESC
            """)
    List<Object[]> findMusicRanking();

    // 최근 N시간 음악별 청취 횟수 (실시간 차트 DB 폴백)
    @Query("""
            SELECT l.music.id, COUNT(l)
            FROM ListenLog l
            WHERE l.listenedAt >= :since
            GROUP BY l.music.id
            ORDER BY COUNT(l) DESC
            """)
    List<Object[]> findMusicRankingSince(@Param("since") LocalDateTime since);

    // 카탈로그 정리 시 참조 무결성 위해 먼저 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM ListenLog l WHERE l.music.id = :musicId")
    void deleteByMusicId(@Param("musicId") Long musicId);
}