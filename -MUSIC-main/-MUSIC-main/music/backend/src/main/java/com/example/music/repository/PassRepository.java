package com.example.music.repository;

import com.example.music.entity.Pass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PassRepository extends JpaRepository<Pass, Long> {

    // 1. 유저 ID, 활성화 여부, 만료일 기준으로 유효한 이용권이 존재하는지 체크 (ListenLogService에서 사용)
    boolean existsByUserIdAndIsActiveTrueAndExpireDateAfter(Long userId, LocalDateTime now);

    // 2. 특정 유저의 모든 이용권 내역 조회
    List<Pass> findByUserId(Long userId);
}