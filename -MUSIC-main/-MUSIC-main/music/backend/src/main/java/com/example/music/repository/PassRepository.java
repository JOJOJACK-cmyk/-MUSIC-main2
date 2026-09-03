package com.example.music.repository;

import com.example.music.entity.Pass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PassRepository extends JpaRepository<Pass, Long> {

    // 유저 ID + 활성화 + 만료일 기준 유효한 이용권 존재 여부
    boolean existsByUser_IdAndIsActiveTrueAndExpireDateAfter(
            Long userId,
            LocalDateTime now
    );

    // 현재 유효한 이용권 중 만료일이 가장 늦은 것 (구독 상태 표시용)
    Optional<Pass> findFirstByUser_IdAndIsActiveTrueAndExpireDateAfterOrderByExpireDateDesc(
            Long userId,
            LocalDateTime now
    );

    // 특정 유저의 모든 이용권 조회
    List<Pass> findByUser_Id(Long userId);
}