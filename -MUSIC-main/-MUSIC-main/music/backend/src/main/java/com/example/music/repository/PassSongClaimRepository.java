package com.example.music.repository;

import com.example.music.entity.PassSongClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PassSongClaimRepository extends JpaRepository<PassSongClaim, Long> {

    long countByPassId(Long passId);

    boolean existsByPassIdAndMusicId(Long passId, Long musicId);

    @Query("SELECT c.musicId FROM PassSongClaim c WHERE c.passId = :passId ORDER BY c.claimedAt")
    List<Long> findMusicIdsByPassId(@Param("passId") Long passId);

    // 곡 삭제(MusicRemover) 시 참조 정리
    @Modifying
    @Transactional
    @Query("DELETE FROM PassSongClaim c WHERE c.musicId = :musicId")
    void deleteByMusicId(@Param("musicId") Long musicId);
}
