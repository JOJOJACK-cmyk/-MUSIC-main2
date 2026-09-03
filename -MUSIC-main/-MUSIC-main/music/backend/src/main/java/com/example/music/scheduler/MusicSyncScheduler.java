package com.example.music.scheduler;

import com.example.music.service.YouTubeApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MusicSyncScheduler {

    private final YouTubeApiService youTubeApiService;

    // 💡 1. 서버 시작 후 5초 뒤에 최초 1회 자동 동기화 실행 (테스트용)
    @Scheduled(initialDelay = 5000, fixedRate = Long.MAX_VALUE)
    public void initialSync() {
        log.info("⏰ [스케줄러] 서버 기동 후 최초 유튜브 플레이리스트 자동 동기화 시작...");
        runSync();
    }

    // 💡 2. 매일 새벽 3시에 정기적으로 실행 (cron 전용)
    @Scheduled(cron = "0 0 3 * * *")
    public void dailySync() {
        log.info("⏰ [스케줄러] 정기 새벽 유튜브 플레이리스트 자동 동기화 시작...");
        runSync();
    }

    // 공통 실행 로직
    private void runSync() {
        try {
            String targetPlaylistId = "PL4fGSI1pDJn6O1LS0XSdF3RyO0Rq_LDeI";
            youTubeApiService.syncPlaylist(targetPlaylistId);
            log.info("✅ [스케줄러] 유튜브 플레이리스트 자동 동기화 완료!");
        } catch (Exception e) {
            log.error("❌ [스케줄러] 동기화 중 오류 발생: ", e);
        }
    }
}