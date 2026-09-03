package com.example.music.scheduler;

import com.example.music.service.YouTubeApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MusicSyncScheduler {

    private final YouTubeApiService youTubeApiService;

    private static final int RESULTS_PER_KEYWORD = 25;

    // "실시간 인기 급상승 곡" 기본 음원 확보용 큐레이션 재생목록
    private static final String BASE_PLAYLIST_ID = "PL4fGSI1pDJn6O1LS0XSdF3RyO0Rq_LDeI";

    // K-POP/J-POP/버튜버 곡 확보용 공식 채널 업로드 재생목록 (UU... = 채널 UC...의 업로드 목록)
    private static final List<String> LABEL_UPLOAD_PLAYLISTS = List.of(
            "UUEf_Bc-KVd7onSeifS3py9g", // SMTOWN
            "UUaO6TYtlC8U5ttz62hTrZgg", // JYP Entertainment
            "UUOmHUn--16B90oW2L6FRR3A", // YG ENTERTAINMENT
            "UUweOkPb1wVVH0Q0Tlj4a5Pw"  // 1theK (원더케이)
    );

    // 핸들(@...)로 업로드 재생목록을 찾아 동기화할 채널 - J-POP / 버튜버 보강용.
    // 없는 핸들은 자동 스킵되므로 후보를 넉넉히 나열한다.
    private static final List<String> CHANNEL_HANDLES = List.of(
            // J-POP
            "@THEFIRSTTAKE", "@Ado1024", "@Ayase_YOASOBI", "@officialhigedandism",
            "@KingGnu", "@MrsGREENAPPLE_Official", "@aimyon36", "@n-buna",
            "@vaundy", "@zutomayo", "@Eve__official", "@backnumber-official-",
            "@SEKAINOOWARI", "@fujiikaze", "@tuki.", "@yoasobi_official_",
            "@yonezuKenshi", "@yorushika_official", "@ROSE-official", "@OfficialHIGE",
            "@sumika_official", "@Vaundy_channel", "@RADWIMPS", "@ldh_channel",
            // 버튜버 / 버추얼
            "@hololive", "@hololiveEnglish", "@holoZ", "@Nijisanji_official",
            "@nijisanjien", "@KAMITSUBAKI_RECORD", "@KAF_official", "@StelLive_kr",
            "@stellive_official", "@WAKTAVERSE", "@PLAVE_official", "@vspo_official",
            "@KanadeIzuru", "@hoshimachisuisei", "@MoriCalliope", "@GawrGura",
            "@HakosBaelz", "@TakanashiKiara", "@usadapekora", "@houshoumarine"
    );

    private static final int PLAYLIST_MAX_ITEMS = 120;
    private static final int CHANNEL_MAX_ITEMS = 45;

    // 카테고리별 최소 보유 목표 곡 수 (미달 시에만 검색으로 보충)
    private static final Map<String, Integer> DEPTH_TARGET = new LinkedHashMap<>() {{
        put("KPOP", 120);
        put("JPOP", 90);
        put("VTUBER", 70);
        put("POP", 150);
    }};

    // 💡 1. 서버 기동 후 10초 뒤: 재생목록 + 인기차트 + 부족한 카테고리 보충
    @Scheduled(initialDelay = 10_000, fixedRate = Long.MAX_VALUE)
    public void initialSync() {
        log.info("⏰ [스케줄러] 서버 기동 후 최신곡 동기화 시작...");
        syncBasePlaylist();
        syncTrending();
        pruneMetadata();
        ensureDepth();
    }

    // 💡 2. 매일 새벽 3시: 전체 갱신 + 카테고리 대량 검색
    @Scheduled(cron = "0 0 3 * * *")
    public void dailySync() {
        log.info("⏰ [스케줄러] 정기(새벽 3시) 유튜브 최신곡 자동 동기화 시작...");
        syncBasePlaylist();
        syncTrending();
        pruneMetadata();
        try {
            Map<String, Integer> result =
                    youTubeApiService.syncLatestMusicForAllCategories(RESULTS_PER_KEYWORD);
            log.info("✅ [스케줄러] 카테고리별 대량 검색 동기화 완료! {}", result);
        } catch (Exception e) {
            log.error("❌ [스케줄러] 카테고리 검색 동기화 중 오류: ", e);
        }
        ensureDepth();
    }

    private void syncBasePlaylist() {
        try {
            int base = youTubeApiService.syncPlaylist(BASE_PLAYLIST_ID).size();
            log.info("📀 [스케줄러] 기본 재생목록 동기화 {}건", base);
        } catch (Exception e) {
            log.error("❌ [스케줄러] 재생목록 동기화 중 오류: ", e);
        }
        for (String pl : LABEL_UPLOAD_PLAYLISTS) {
            try {
                int n = youTubeApiService.syncPlaylist(pl, PLAYLIST_MAX_ITEMS).size();
                log.info("📀 [스케줄러] 채널 업로드({}) 동기화 {}건", pl, n);
            } catch (Exception e) {
                log.warn("채널 업로드 재생목록 스킵 ({}): {}", pl, e.getMessage());
            }
        }
        int handleTotal = 0;
        for (String handle : CHANNEL_HANDLES) {
            try {
                int n = youTubeApiService.syncChannelByHandle(handle, CHANNEL_MAX_ITEMS).size();
                if (n > 0) log.info("📺 [스케줄러] 채널({}) 동기화 {}건", handle, n);
                handleTotal += n;
            } catch (Exception e) {
                log.warn("채널 스킵 ({}): {}", handle, e.getMessage());
            }
        }
        log.info("📺 [스케줄러] 핸들 채널 총 {}건 동기화", handleTotal);
    }

    private void syncTrending() {
        try {
            Map<String, Integer> trending = youTubeApiService.syncTrendingMusic();
            log.info("🔥 [스케줄러] 지역별 인기 음악 차트 동기화 완료! {}", trending);
        } catch (Exception e) {
            log.error("❌ [스케줄러] 인기 차트 동기화 중 오류: ", e);
        }
    }

    private void pruneMetadata() {
        try {
            youTubeApiService.backfillAndPruneMetadata();
        } catch (Exception e) {
            log.error("❌ [스케줄러] 메타데이터 백필 중 오류: ", e);
        }
    }

    private void ensureDepth() {
        try {
            Map<String, Integer> added = youTubeApiService.ensureCatalogDepth(DEPTH_TARGET, RESULTS_PER_KEYWORD);
            if (!added.isEmpty()) log.info("📚 [스케줄러] 카탈로그 보충 완료! {}", added);
        } catch (Exception e) {
            log.error("❌ [스케줄러] 카탈로그 보충 중 오류: ", e);
        }
    }
}
