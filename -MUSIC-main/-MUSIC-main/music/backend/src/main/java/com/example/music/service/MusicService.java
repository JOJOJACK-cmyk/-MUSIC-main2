package com.example.music.service;

import com.example.music.dto.MusicDto;
import com.example.music.dto.YouTubeVideoDto;
import com.example.music.entity.LikedMusic;
import com.example.music.entity.Music;
import com.example.music.entity.User;
import com.example.music.repository.LikedMusicRepository;
import com.example.music.repository.MusicRepository;
import com.example.music.repository.UserRepository;
import com.example.music.security.AuthenticatedUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MusicService {

    private final MusicRepository musicRepository;
    private final YouTubeApiService youTubeApiService;
    private final UserRepository userRepository;
    private final LikedMusicRepository likedMusicRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    // 검색어별 마지막 유튜브 보강 시각 (재검색·할당량 낭비 방지, 재시작 시 초기화)
    private final Map<String, Instant> youtubeSearchAt = new ConcurrentHashMap<>();
    private static final Duration YT_SEARCH_TTL = Duration.ofHours(12);
    private static final int DB_ENOUGH_RESULTS = 20;   // 이보다 적으면 유튜브에서 보강
    // search.list 는 결과 수와 무관하게 100유닛 → 한 번 쓸 때 최대치(50)로 뽑아 최대한 많이 확보
    private static final int YT_FETCH_PER_SEARCH = 45;

    // 할당량/레이트리밋(403·429) 초과 시 전체 유튜브 보강을 이 시각까지 중단
    private volatile Instant youtubeCooldownUntil = Instant.EPOCH;
    private static final Duration YT_COOLDOWN = Duration.ofMinutes(20);
    // 동시 검색이 유튜브를 몰아치지 않도록 직렬화
    private final Object youtubeLock = new Object();

    // 💡 장르 판별 로직은 YouTubeApiService 를 단일 소스로 위임한다 (중복/불일치 제거)
    public String determineGenre(String title, String artist) {
        return youTubeApiService.determineGenre(title, artist);
    }

    @Transactional
    public MusicDto.Response createMusic(MusicDto.CreateRequest request) {
        Optional<Music> existingMusic = musicRepository.findByYoutubeVideoId(request.getYoutubeVideoId());

        if (existingMusic.isPresent()) {
            return new MusicDto.Response(existingMusic.get());
        }

        String resolvedGenre = (request.getGenre() != null && !request.getGenre().isBlank())
                ? request.getGenre().toUpperCase()
                : determineGenre(request.getTitle(), request.getArtist());

        Music musicEntity = Music.builder()
                .youtubeVideoId(request.getYoutubeVideoId())
                .title(request.getTitle())
                .artist(request.getArtist())
                .thumbnailUrl(request.getThumbnailUrl())
                .genre(resolvedGenre)
                .manualAdd(true)
                .build();

        Music music = musicRepository.save(musicEntity);
        return new MusicDto.Response(music);
    }

    public MusicDto.Response getMusic(Long id) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("음원을 찾을 수 없습니다. id=" + id));
        return new MusicDto.Response(music);
    }

    @Transactional
    public List<MusicDto.Response> getAllMusic() {
        List<Music> allMusic = musicRepository.findAll();

        // ※ 정렬/최신곡 필터는 하지 않는다. "실시간 인기 급상승 곡"(상위 10곡)은 프론트가
        //   API 순서 그대로 slice(0,10) 하므로 여기서 순서를 바꾸면 안 된다.
        //   "최신곡만" 노출은 프론트의 카테고리 섹션에서만 적용한다.
        return dedupeByTitle(allMusic).stream()
                .filter(music -> {
                    // 💡 관리자가 URL 을 직접 등록한 곡은 자동 정리 대상에서 제외 (길이/키워드 무관하게 노출)
                    if (Boolean.TRUE.equals(music.getManualAdd())) {
                        return true;
                    }

                    String a = music.getArtist() != null ? music.getArtist().toLowerCase() : "";
                    Long duration = music.getDurationSeconds();

                    // 1) 제목 기반 비음악(쇼츠/토크/플레이리스트/커버 등) · 아티스트 cut · 비공식 채널 → 삭제
                    boolean nonMusicTitle = youTubeApiService.isNonMusicTitle(music.getTitle())
                            || a.contains("cut")
                            || youTubeApiService.isNonMusicChannel(music.getArtist());

                    // 2) 재생 시간이 저장되어 있고 단곡 범위(90~480초)를 벗어나면(쇼츠/장편) → 삭제
                    boolean badDuration = duration != null && !youTubeApiService.isValidSongDuration(duration);

                    if (nonMusicTitle || badDuration) {
                        try {
                            musicRepository.delete(music);
                        } catch (Exception e) {}
                        return false;
                    }
                    return true;
                })
                .map(music -> {
                    // 곡 자체(문자/아티스트/버튜버 마커)로 재판별. 단, "POP"은 판별 실패 기본값이므로
                    // 지역 힌트로 이미 KPOP/JPOP/VTUBER 로 저장된 값을 POP 으로 덮어쓰지 않는다.
                    String detected = determineGenre(music.getTitle(), music.getArtist());
                    boolean stored = music.getGenre() != null && !music.getGenre().isBlank();
                    boolean confident = !"POP".equals(detected);
                    if (!stored || (confident && !detected.equals(music.getGenre()))) {
                        music.update(music.getTitle(), music.getArtist(), music.getThumbnailUrl(), detected);
                        musicRepository.save(music);
                    }
                    return new MusicDto.Response(music);
                })
                .collect(Collectors.toList());
    }

    /**
     * 제목·아티스트 키워드 검색.
     * DB에 등록된 곡이 부족하면(그리고 최근에 안 했으면) YouTube 검색으로 곡을 즉시 가져와 등록한 뒤 다시 조회한다.
     * (HTTP 호출이 있으므로 트랜잭션 밖에서 수행 — NOT_SUPPORTED)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MusicDto.Response> searchMusics(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isBlank()) return List.of();
        // 💡 띄어쓰기 관계없이 제목/아티스트만 맞으면 검색되도록, 공백을 지우고 비교한다.
        String kwNoSpaces = kw.replace(" ", "");

        List<Music> local = musicRepository.findByTitleOrArtistIgnoringSpaces(kwNoSpaces);

        if (shouldEnrichFromYoutube(kw, local.size())) {
            synchronized (youtubeLock) {
                // 락 대기 중 다른 요청이 이미 채웠거나 쿨다운에 걸렸을 수 있으니 재확인
                if (shouldEnrichFromYoutube(kw, local.size())) {
                    youtubeSearchAt.put(kw.toLowerCase(), Instant.now());
                    try {
                        youTubeApiService.syncLatestMusicByKeyword(kw, YT_FETCH_PER_SEARCH);
                    } catch (YouTubeApiService.QuotaExceededException qe) {
                        youtubeCooldownUntil = Instant.now().plus(YT_COOLDOWN);
                        log.warn("[Search] 유튜브 검색 할당량 초과 - {}분간 보강 중단", YT_COOLDOWN.toMinutes());
                    } catch (Exception e) {
                        log.warn("[Search] 유튜브 보강 실패 (keyword='{}'): {}", kw, e.getMessage());
                    }
                    local = musicRepository.findByTitleOrArtistIgnoringSpaces(kwNoSpaces);
                }
            }
        }

        return dedupeByTitle(local).stream()
                .map(MusicDto.Response::new)
                .collect(Collectors.toList());
    }

    /**
     * 같은 곡이 다른 videoId 로 여러 개 들어온 경우 정리 — 정규화한 제목으로 묶고, 그 안에서
     * 재생시간(또는 아티스트)이 같은 것만 "같은 녹음"으로 보아 대표 1곡만 남긴다.
     * (레이블/업로더 채널마다 아티스트 표기가 크게 달라 제목만으로 우선 묶는다 — [[YouTubeApiService.isSameRecording]])
     */
    private List<Music> dedupeByTitle(List<Music> list) {
        java.util.LinkedHashMap<String, List<Music>> groups = new java.util.LinkedHashMap<>();
        for (Music m : list) {
            String nt = YouTubeApiService.normalizeTitleForDedupe(m.getTitle());
            // 정규화 후 비면 병합하지 않고 그대로 노출 (videoId 로 고유 키 유지)
            String key = nt.isEmpty() ? ("__" + m.getYoutubeVideoId()) : nt;
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(m);
        }

        List<Music> result = new java.util.ArrayList<>();
        for (List<Music> group : groups.values()) {
            List<Music> reps = new java.util.ArrayList<>(); // 같은 제목이지만 서로 다른 실제 곡일 수 있는 대표들
            for (Music m : group) {
                Music match = null;
                for (Music r : reps) {
                    if (YouTubeApiService.isSameRecording(r, m)) { match = r; break; }
                }
                if (match == null) {
                    reps.add(m);
                } else if (YouTubeApiService.isBetterRepresentative(m, match)) {
                    reps.set(reps.indexOf(match), m);
                }
            }
            result.addAll(reps);
        }
        return result;
    }

    private boolean shouldEnrichFromYoutube(String keyword, int localCount) {
        if (keyword.length() < 2) return false;               // 1글자 검색은 유튜브 호출 안 함
        if (localCount >= DB_ENOUGH_RESULTS) return false;     // 이미 충분
        if (Instant.now().isBefore(youtubeCooldownUntil)) return false;  // 할당량 초과 쿨다운 중
        Instant last = youtubeSearchAt.get(keyword.toLowerCase());
        return last == null || Duration.between(last, Instant.now()).compareTo(YT_SEARCH_TTL) >= 0;
    }

    @Transactional
    public MusicDto.Response updateMusic(Long id, MusicDto.UpdateRequest request) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("음원을 찾을 수 없습니다. id=" + id));

        String targetThumbnail = (request.getThumbnailUrl() != null && !request.getThumbnailUrl().isBlank())
                ? request.getThumbnailUrl()
                : music.getThumbnailUrl();

        String targetGenre = (request.getGenre() != null && !request.getGenre().isBlank())
                ? request.getGenre().toUpperCase()
                : determineGenre(
                request.getTitle() != null ? request.getTitle() : music.getTitle(),
                request.getArtist() != null ? request.getArtist() : music.getArtist()
        );

        music.update(
                request.getTitle() != null ? request.getTitle() : music.getTitle(),
                request.getArtist() != null ? request.getArtist() : music.getArtist(),
                targetThumbnail,
                targetGenre
        );
        return new MusicDto.Response(music);
    }

    @Transactional
    public void deleteMusic(Long id) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("음원을 찾을 수 없습니다. id=" + id));
        musicRepository.delete(music);
    }

    // 💡 카테고리별(KPOP/JPOP/VTUBER/POP) 유튜브 최신곡 수동 동기화 (관리자용/테스트용)
    @Transactional
    public Map<String, Integer> syncLatestMusicForAllCategories(int perKeyword) {
        return youTubeApiService.syncLatestMusicForAllCategories(perKeyword);
    }

    // 💡 지역별 인기 음악 차트 동기화 (저렴 - search 할당량 소진 없음)
    @Transactional
    public Map<String, Integer> syncTrendingMusic() {
        return youTubeApiService.syncTrendingMusic();
    }

    // 💡 [관리자] DB의 모든 곡 재검증 → 비음악 영상 삭제 (저렴 - videos.list 배치)
    //    (내부에서 삭제를 트랜잭션 밖으로 독립 실행하므로 여기서는 tx 를 열지 않는다)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Integer> pruneNonMusicCatalog() {
        return youTubeApiService.pruneNonMusicCatalog();
    }

    @Transactional
    public MusicDto.Response createMusicFromYouTube(String videoId) throws Exception {
        // 관리자가 URL 을 직접 붙여넣어 등록하는 경로.
        // 자동 동기화용 "단곡 게이트"(재생시간 90~480초·비음악 키워드·임베드·카테고리)를 적용하면
        // 8분 넘는 MV·라이브·제목에 live/cover 등이 든 정상 곡까지 400 으로 막히므로,
        // 여기서는 메타데이터만 가져와 그대로 저장한다. (영상이 없을 때만 실패)
        YouTubeVideoDto video = youTubeApiService.getVideoInfoLenient(videoId);

        Music music = musicRepository.findByYoutubeVideoId(video.getYoutubeVideoId())
                .orElseGet(() -> {
                    String inferredGenre = determineGenre(video.getTitle(), video.getArtist());
                    Music newMusic = Music.builder()
                            .youtubeVideoId(video.getYoutubeVideoId())
                            .title(video.getTitle())
                            .artist(video.getArtist())
                            .thumbnailUrl(video.getThumbnailUrl())
                            .genre(inferredGenre)
                            .durationSeconds(video.getDurationSeconds())
                            .build();
                    return musicRepository.save(newMusic);
                });

        return new MusicDto.Response(music);
    }

    /**
     * 로그인 사용자 해석. 일반 로그인(이메일 principal/토큰 필터)·OAuth2 세션(소셜 provider 숫자 id
     * name + 중첩 email 속성) 을 모두 처리하는 {@link AuthenticatedUserResolver} 로 위임한다.
     * 해석 실패 시 실제 원인을 로그로 남긴다. (org.springframework.security.access.AccessDeniedException 전파)
     */
    private User getUserFromAuthentication(Authentication authentication) {
        try {
            return authenticatedUserResolver.resolveRequiredUser(authentication);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            log.warn("[좋아요] 인증 사용자 해석 실패 - type={}, name={}, principal={}, reason={}",
                    authentication == null ? "null" : authentication.getClass().getSimpleName(),
                    authentication == null ? "null" : authentication.getName(),
                    authentication == null ? "null" : String.valueOf(authentication.getPrincipal()),
                    e.getMessage());
            throw e;
        }
    }

    @Transactional
    public boolean toggleLikeMusic(Authentication authentication, Long musicId) {
        User user = getUserFromAuthentication(authentication);

        Music music = musicRepository.findById(musicId)
                .orElseThrow(() -> new IllegalArgumentException("음원을 찾을 수 없습니다. id=" + musicId));

        Optional<LikedMusic> existingLike = likedMusicRepository.findByUserAndMusic(user, music);

        if (existingLike.isPresent()) {
            likedMusicRepository.delete(existingLike.get());
            return false;
        }

        // 더블클릭/동시요청으로 유니크 제약(uk_user_music_like) 충돌이 나지 않도록 저장 직전 재확인
        if (likedMusicRepository.existsByUserAndMusic(user, music)) {
            return true;
        }

        LikedMusic likedMusic = LikedMusic.builder()
                .user(user)
                .music(music)
                .build();
        likedMusicRepository.save(likedMusic);
        return true;
    }

    public List<MusicDto.Response> getLikedMusics(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        List<LikedMusic> likedMusics = likedMusicRepository.findByUser(user);
        return likedMusics.stream()
                .map(liked -> new MusicDto.Response(liked.getMusic()))
                .collect(Collectors.toList());
    }
}