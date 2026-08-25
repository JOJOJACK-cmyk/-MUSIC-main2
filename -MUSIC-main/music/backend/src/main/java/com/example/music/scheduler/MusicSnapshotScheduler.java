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

        // 전체 음악 목록 Redis 저장
        musicSnapshotService.saveAllMusicSnapshot();

        // 청취 로그 기반 TOP100 Redis 저장
        musicSnapshotService.saveTop100Ranking();

        System.out.println("Redis music snapshot + TOP100 refreshed");
    }
}