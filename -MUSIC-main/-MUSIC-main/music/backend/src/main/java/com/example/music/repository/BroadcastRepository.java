package com.example.music.repository;

import com.example.music.entity.Broadcast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    // 유저 ID를 통해 방송 정보 조회
    Optional<Broadcast> findByUser_Id(Long userId);

    // 스트림 키를 통해 방송 정보 조회 (미디어 서버 연동 및 방송 시작 검증용)
    Optional<Broadcast> findByStreamKey(String streamKey);

    List<Broadcast> findByStreamKeyIn(Collection<String> streamKeys);


}