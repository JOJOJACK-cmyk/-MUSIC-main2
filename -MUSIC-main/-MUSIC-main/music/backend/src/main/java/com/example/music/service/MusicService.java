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

    // 💡 좋아요 기능에 필요한 Repository 추가 주입
    private final UserRepository userRepository;
    private final LikedMusicRepository likedMusicRepository;

    @Transactional
    public MusicDto.Response createMusic(MusicDto.CreateRequest request) {
        // 중복 방지 (이미 존재하는 youtubeVideoId인지 체크)
        musicRepository.findByYoutubeVideoId(request.getYoutubeVideoId()).ifPresent(m -> {
            throw new IllegalArgumentException("이미 등록된 음원(영상)입니다.");
        });

        Music music = musicRepository.save(request.toEntity());
        return new MusicDto.Response(music);
    }

    public MusicDto.Response getMusic(Long id) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("음원을 찾을 수 없습니다. id=" + id));
        return new MusicDto.Response(music);
    }

    public List<MusicDto.Response> getAllMusic() {
        return musicRepository.findAll().stream()
                .map(MusicDto.Response::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public MusicDto.Response updateMusic(Long id, MusicDto.UpdateRequest request) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("음원을 찾을 수 없습니다. id=" + id));

        // 썸네일 URL이 null이거나 비어있을 경우 기존 엔티티의 썸네일 URL 유지
        String targetThumbnail = (request.getThumbnailUrl() != null && !request.getThumbnailUrl().isBlank())
                ? request.getThumbnailUrl()
                : music.getThumbnailUrl();

        music.update(request.getTitle(), request.getArtist(), targetThumbnail);
        return new MusicDto.Response(music);
    }

    @Transactional
    public void deleteMusic(Long id) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("음원을 찾을 수 없습니다. id=" + id));
        musicRepository.delete(music);
    }

    @Transactional
    public MusicDto.Response createMusicFromYouTube(String videoId) throws Exception {
        // 1. YouTubeApiService를 호출하여 영상 조회 (없으면 API 호출 후 내부에서 DB 자동 캐싱 저장)
        YouTubeVideoDto video = youTubeApiService.getVideoInfo(videoId);

        // 2. 캐싱되어 DB에 저장된 Music Entity를 조회하여 반환 (이중 save 충돌 방지)
        Music music = musicRepository.findByYoutubeVideoId(video.getYoutubeVideoId())
                .orElseThrow(() -> new IllegalStateException("YouTube 음원 정보 조회 및 캐싱에 실패했습니다. videoId=" + videoId));

        return new MusicDto.Response(music);
    }

    // 💡 공통: 소셜 로그인 및 일반 로그인 모두 대응하여 유저를 안전하게 찾아내는 헬퍼 메서드
    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        String identifier = authentication.getName();

        // 1. 이메일로 먼저 조회 시도
        Optional<User> userOpt = userRepository.findByEmail(identifier);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }

        // 2. 닉네임으로 조회 시도
        userOpt = userRepository.findByNickname(identifier);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }

        // 3. OAuth2 소셜 로그인인 경우 principal attributes에서 이메일 추출 시도
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

    // 💡 OAuth2 Attributes에서 플랫폼별 이메일 추출을 담당하는 보조 메서드
    private String extractEmailFromOAuth2Attributes(Map<String, Object> attributes) {
        if (attributes == null) return null;

        if (attributes.containsKey("kakao_account")) {
            Map<?, ?> kakaoAccount = (Map<?, ?>) attributes.get("kakao_account");
            if (kakaoAccount != null && kakaoAccount.containsKey("email")) {
                return (String) kakaoAccount.get("email");
            }
        }
        if (attributes.containsKey("response")) { // 네이버
            Map<?, ?> naverResp = (Map<?, ?>) attributes.get("response");
            if (naverResp != null && naverResp.containsKey("email")) {
                return (String) naverResp.get("email");
            }
        }
        if (attributes.containsKey("email")) { // 구글 등
            return (String) attributes.get("email");
        }

        return null;
    }

    // ==========================================
    // 💡 음원 좋아요(보관함 담기/취소) 토글 로직
    // ==========================================
    @Transactional
    public boolean toggleLikeMusic(Authentication authentication, Long musicId) {
        User user = getUserFromAuthentication(authentication);

        Music music = musicRepository.findById(musicId)
                .orElseThrow(() -> new IllegalArgumentException("음원을 찾을 수 없습니다. id=" + musicId));

        Optional<LikedMusic> existingLike = likedMusicRepository.findByUserAndMusic(user, music);

        if (existingLike.isPresent()) {
            // 이미 좋아요를 눌렀다면 삭제 (취소)
            likedMusicRepository.delete(existingLike.get());
            return false; // 좋아요 해제됨
        } else {
            // 좋아요가 없다면 새로 생성 (등록)
            LikedMusic likedMusic = LikedMusic.builder()
                    .user(user)
                    .music(music)
                    .build();
            likedMusicRepository.save(likedMusic);
            return true; // 좋아요 등록됨
        }
    }

    // ==========================================
    // 💡 내 보관함(좋아요 누른 음악) 목록 조회 로직
    // ==========================================
    public List<MusicDto.Response> getLikedMusics(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);

        List<LikedMusic> likedMusics = likedMusicRepository.findByUser(user);

        return likedMusics.stream()
                .map(liked -> new MusicDto.Response(liked.getMusic()))
                .collect(Collectors.toList());
    }
}