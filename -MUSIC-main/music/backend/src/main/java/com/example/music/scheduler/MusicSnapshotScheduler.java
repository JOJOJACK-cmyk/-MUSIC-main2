package com.example.music.scheduler;

import com.example.music.service.MusicSnapshotService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MusicSnapshotScheduler {

    private final MusicSnapshotService musicSnapshotService;

    public MusicSnapshotScheduler(
            MusicSnapshotService musicSnapshotService
    ) {
        this.musicSnapshotService = musicSnapshotService;
    }

    @Scheduled(fixedRateString = "${snapshot.music.interval}")
    public void refreshMusicSnapshot() {

        musicSnapshotService.saveAllMusicSnapshot();

        System.out.println("Redis music snapshot refreshed");
    }
}