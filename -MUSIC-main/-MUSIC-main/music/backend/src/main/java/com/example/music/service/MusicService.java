package com.example.music.service;

import com.example.music.dto.MusicDto;
import com.example.music.dto.YouTubeVideoDto;
import com.example.music.entity.LikedMusic;
import com.example.music.entity.Music;
import com.example.music.entity.User;
import com.example.music.repository.LikedMusicRepository;
import com.example.music.repository.MusicRepository;
import com.example.music.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MusicService {

    private final MusicRepository musicRepository;
    private final YouTubeApiService youTubeApiService;
    private final UserRepository userRepository;
    private final LikedMusicRepository likedMusicRepository;

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
        return allMusic.stream()
                .filter(music -> {
                    String a = music.getArtist() != null ? music.getArtist().toLowerCase() : "";
                    Long duration = music.getDurationSeconds();

                    // 1) 제목 기반 비음악(쇼츠/토크/플레이리스트/커버 등) 또는 아티스트에 cut 포함 → 삭제
                    boolean nonMusicTitle = youTubeApiService.isNonMusicTitle(music.getTitle()) || a.contains("cut");

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

    public List<MusicDto.Response> searchMusics(String keyword) {
        List<Music> musicList = musicRepository.findByTitleContainingIgnoreCaseOrArtistContainingIgnoreCase(keyword, keyword);
        return musicList.stream()
                .map(MusicDto.Response::new)
                .collect(Collectors.toList());
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

    @Transactional
    public MusicDto.Response createMusicFromYouTube(String videoId) throws Exception {
        YouTubeVideoDto video = youTubeApiService.getVideoInfo(videoId);

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

    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        String identifier = authentication.getName();

        Optional<User> userOpt = userRepository.findByEmail(identifier);
        if (userOpt.isPresent()) return userOpt.get();

        userOpt = userRepository.findByNickname(identifier);
        if (userOpt.isPresent()) return userOpt.get();

        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User) {
            OAuth2User oAuth2User = (OAuth2User) principal;
            String extractedEmail = extractEmailFromOAuth2Attributes(oAuth2User.getAttributes());

            if (extractedEmail != null && !extractedEmail.isEmpty()) {
                final String targetEmail = extractedEmail;
                return userRepository.findByEmail(targetEmail)
                        .orElseThrow(() -> new IllegalArgumentException("소셜 이메일에 해당하는 유저를 찾을 수 없습니다: " + targetEmail));
            }
        }

        throw new IllegalArgumentException("유저를 찾을 수 없습니다. (identifier: " + identifier + ")");
    }

    private String extractEmailFromOAuth2Attributes(Map<String, Object> attributes) {
        if (attributes == null) return null;

        if (attributes.containsKey("kakao_account")) {
            Map<?, ?> kakaoAccount = (Map<?, ?>) attributes.get("kakao_account");
            if (kakaoAccount != null && kakaoAccount.containsKey("email")) {
                return (String) kakaoAccount.get("email");
            }
        }
        if (attributes.containsKey("response")) {
            Map<?, ?> naverResp = (Map<?, ?>) attributes.get("response");
            if (naverResp != null && naverResp.containsKey("email")) {
                return (String) naverResp.get("email");
            }
        }
        if (attributes.containsKey("email")) {
            return (String) attributes.get("email");
        }

        return null;
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
        } else {
            LikedMusic likedMusic = LikedMusic.builder()
                    .user(user)
                    .music(music)
                    .build();
            likedMusicRepository.save(likedMusic);
            return true;
        }
    }

    public List<MusicDto.Response> getLikedMusics(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        List<LikedMusic> likedMusics = likedMusicRepository.findByUser(user);
        return likedMusics.stream()
                .map(liked -> new MusicDto.Response(liked.getMusic()))
                .collect(Collectors.toList());
    }
}