package com.example.music.repository;

import com.example.music.entity.Pass;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // 아직 끝나지 않은 이용권 전부 (지금 쓰는 것 + 연장 결제로 예약된 것), 시작일 순
    List<Pass> findByUser_IdAndIsActiveTrueAndExpireDateAfterOrderByStartDateAsc(Long userId, LocalDateTime now);

    // 곡 수 제한 이용권 차감을 직렬화 (동시에 두 곡을 들어도 한도를 넘지 않게)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pass p WHERE p.id = :id")
    Optional<Pass> lockById(@Param("id") Long id);

    // 특정 유저의 모든 이용권 조회
    List<Pass> findByUser_Id(Long userId);

    // 이용권 만료 임박 알림 대상 - 기간 안에 만료되는 유효 이용권
    List<Pass> findByIsActiveTrueAndExpireDateBetween(LocalDateTime from, LocalDateTime to);
}
