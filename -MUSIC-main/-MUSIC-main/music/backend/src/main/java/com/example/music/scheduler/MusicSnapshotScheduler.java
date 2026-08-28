package com.example.music.scheduler;

import com.example.music.service.MusicSnapshotService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MusicSnapshotScheduler {

    private final MusicSnapshotService musicSnapshotService;

    public MusicSnapshotScheduler(MusicSnapshotService musicSnapshotService) {
        this.musicSnapshotService = musicSnapshotService;
    }

    @Scheduled(fixedRateString = "${snapshot.music.interval}")
    public void refreshMusicSnapshot() {
        try {
            // 전체 음악 목록 Redis 저장
            musicSnapshotService.saveAllMusicSnapshot();

            // 청취 로그 기반 TOP100 Redis 저장
            musicSnapshotService.saveTop100Ranking();

            System.out.println("Redis music snapshot + TOP100 refreshed");
        } catch (Exception e) {
            // 테스트 환경이나 Redis 커넥션 종료 시 발생하는 예외를 안전하게 방어
            System.err.println("Redis 스냅샷 갱신 중 예외 발생 (무시됨): " + e.getMessage());
        }
    }
}