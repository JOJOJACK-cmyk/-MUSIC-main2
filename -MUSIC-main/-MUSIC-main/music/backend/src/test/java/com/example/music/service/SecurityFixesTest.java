package com.example.music.service;

import com.example.music.entity.LikedMusic;
import com.example.music.entity.Music;
import com.example.music.entity.User;
import com.example.music.repository.LikedMusicRepository;
import com.example.music.repository.MusicRepository;
import com.example.music.repository.UserRepository;
import com.example.music.security.OAuthAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 2026-10-01 검토에서 고친 문제들의 회귀 테스트 */
@SpringBootTest
class SecurityFixesTest {

    @Autowired private MusicService musicService;
    @Autowired private MusicRepository musicRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LikedMusicRepository likedMusicRepository;
    @Autowired private CustomOAuth2UserService oAuth2UserService;
    @Autowired private ChartService chartService;
    @Autowired private StringRedisTemplate redis;

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** 좋아요가 있는 곡이 정리 대상이어도 목록 조회가 실패하지 않고, 곡과 좋아요가 함께 정리된다 */
    @Test
    void 좋아요_있는_정리대상_곡이_있어도_목록조회_성공() {
        String t = tag();
        User u = userRepository.save(User.builder().email("fx-" + t + "@test.com").nickname("fx" + t).provider("local").build());
        Music junk = musicRepository.save(Music.builder().youtubeVideoId("fx" + t).title("fx " + t + " #shorts").artist("x").build());
        likedMusicRepository.save(LikedMusic.builder().user(u).music(junk).build());
        try {
            musicService.getAllMusic(); // 예전엔 FK 오류로 여기서 예외
            assertThat(musicRepository.findById(junk.getId())).isEmpty();
        } finally {
            likedMusicRepository.deleteByMusicId(junk.getId());
            musicRepository.findById(junk.getId()).ifPresent(musicRepository::delete);
            userRepository.deleteById(u.getId());
        }
    }

    @Test
    @Transactional
    void 같은_이메일의_일반계정에_소셜로_연결되면_비밀번호가_무효화된다() {
        String t = tag();
        String email = "link-" + t + "@test.com";
        userRepository.save(User.builder().email(email).nickname("lk" + t).password("hashed").provider("local").build());

        OAuthAttributes attrs = OAuthAttributes.of("google", "sub", Map.of(
                "sub", "g-" + t, "email", email, "email_verified", true, "name", "Someone"));
        User linked = oAuth2UserService.saveOrUpdate("google", attrs);

        assertThat(linked.getPassword()).isNull();       // 미리 가입해 둔 사람의 비밀번호로는 더 이상 로그인 불가
        assertThat(linked.getProvider()).isEqualTo("google");
        assertThat(linked.getNickname()).isEqualTo("lk" + t); // 닉네임은 덮어쓰지 않음
    }

    @Test
    @Transactional
    void 소셜_신규가입_닉네임이_겹치면_다른_이름으로_만든다() {
        String t = tag();
        userRepository.save(User.builder().email("dup1-" + t + "@test.com").nickname("철수" + t).provider("local").build());
        OAuthAttributes attrs = OAuthAttributes.of("google", "sub", Map.of(
                "sub", "g2-" + t, "email", "dup2-" + t + "@test.com", "email_verified", true, "name", "철수" + t));
        User created = oAuth2UserService.saveOrUpdate("google", attrs);
        assertThat(created.getNickname()).startsWith("철수").isNotEqualTo("철수" + t);
        assertThat(created.getProvider()).isEqualTo("google");
    }

    @Test
    void 카카오_미인증_이메일은_인증되지_않은_것으로_본다() {
        OAuthAttributes unverified = OAuthAttributes.of("kakao", "id", Map.of(
                "id", 1L,
                "kakao_account", Map.of("email", "x@test.com", "is_email_valid", true, "is_email_verified", false,
                        "profile", Map.of("nickname", "k"))));
        OAuthAttributes verified = OAuthAttributes.of("kakao", "id", Map.of(
                "id", 2L,
                "kakao_account", Map.of("email", "y@test.com", "is_email_valid", true, "is_email_verified", true,
                        "profile", Map.of("nickname", "k"))));
        assertThat(unverified.isEmailVerified()).isFalse();
        assertThat(verified.isEmailVerified()).isTrue();
    }

    @Test
    void 닉네임_규칙() {
        assertThatThrownBy(() -> UserService.validateNickname("게스트")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserService.validateNickname("a")).isInstanceOf(IllegalArgumentException.class);
        assertThat(UserService.validateNickname("  음악사랑  ")).isEqualTo("음악사랑");
    }

    @Test
    void 차트는_같은_사용자의_같은_곡을_시간당_한번만_센다() {
        long userId = -777L;
        long musicId = -888L;
        long hour = java.time.Instant.now().getEpochSecond() / 3600;
        try {
            assertThat(chartService.recordListen(userId, musicId)).isTrue();
            assertThat(chartService.recordListen(userId, musicId)).isFalse();
            assertThat(chartService.recordListen(userId - 1, musicId)).isTrue(); // 다른 사용자는 별도
            Double score = redis.opsForZSet().score("chart:h:" + hour, String.valueOf(musicId));
            assertThat(score).isEqualTo(2.0);
        } finally {
            redis.opsForZSet().remove("chart:h:" + hour, String.valueOf(musicId));
            redis.delete(List.of("chart:dedupe:" + hour + ":" + userId + ":" + musicId,
                    "chart:dedupe:" + hour + ":" + (userId - 1) + ":" + musicId));
        }
    }
}
