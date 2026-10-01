package com.example.music.service;

import com.example.music.entity.Music;
import com.example.music.entity.User;
import com.example.music.repository.MusicRepository;
import com.example.music.repository.PlaylistItemRepository;
import com.example.music.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional // 테스트 데이터는 끝나면 롤백
class PlaylistServiceTest {

    @Autowired private PlaylistService playlistService;
    @Autowired private UserRepository userRepository;
    @Autowired private MusicRepository musicRepository;
    @Autowired private PlaylistItemRepository playlistItemRepository;
    @Autowired private EntityManager em;

    private User owner;
    private User other;
    private Music song;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        owner = userRepository.save(User.builder().email("pl-owner-" + tag + "@test.com")
                .nickname("plo" + tag).provider("local").build());
        other = userRepository.save(User.builder().email("pl-other-" + tag + "@test.com")
                .nickname("plx" + tag).provider("local").build());
        song = musicRepository.save(Music.builder().youtubeVideoId("test" + tag)
                .title("테스트 곡").artist("테스트 가수").build());
    }

    @Test
    void 만들고_담고_중복은_막고_빼기() {
        Long id = (Long) playlistService.create(owner, "  드라이브  ").get("id");

        Map<String, Object> after = playlistService.addTrack(owner, id, song.getId());
        assertThat(after.get("trackCount")).isEqualTo(1L);
        assertThat(after.get("name")).isEqualTo("드라이브");

        assertThatThrownBy(() -> playlistService.addTrack(owner, id, song.getId()))
                .isInstanceOf(PlaylistService.DuplicateTrackException.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tracks = (List<Map<String, Object>>) playlistService.detail(owner, id).get("tracks");
        assertThat(tracks).hasSize(1);

        playlistService.removeTrack(owner, id, (Long) tracks.get(0).get("itemId"));
        assertThat(playlistService.detail(owner, id).get("trackCount")).isEqualTo(0L);
    }

    @Test
    void 남의_플레이리스트는_접근_불가() {
        Long id = (Long) playlistService.create(owner, "비공개").get("id");

        assertThatThrownBy(() -> playlistService.detail(other, id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> playlistService.addTrack(other, id, song.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> playlistService.delete(other, id)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 곡이_삭제되면_플레이리스트에서도_빠진다() {
        Long id = (Long) playlistService.create(owner, "삭제 테스트").get("id");
        playlistService.addTrack(owner, id, song.getId());
        em.flush();
        em.clear();

        // 관리자 삭제/카탈로그 정리처럼 playlist_item 을 모르는 코드가 곡을 지워도 FK 오류 없이 함께 지워져야 한다
        em.createQuery("delete from Music m where m.id = :id").setParameter("id", song.getId()).executeUpdate();
        em.clear();

        assertThat(playlistItemRepository.countByPlaylist_Id(id)).isZero();
    }
}
