package com.example.music.service;

import com.example.music.dto.MusicDto;
import com.example.music.entity.Music;
import com.example.music.entity.Playlist;
import com.example.music.entity.PlaylistItem;
import com.example.music.entity.User;
import com.example.music.repository.MusicRepository;
import com.example.music.repository.PlaylistItemRepository;
import com.example.music.repository.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    public static final int MAX_PLAYLISTS = 50;
    public static final int MAX_TRACKS = 500;
    private static final int MAX_NAME_LENGTH = 60;

    private final PlaylistRepository playlistRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final MusicRepository musicRepository;

    /** 이미 담긴 곡을 다시 담으려 할 때 (→ 409) */
    public static class DuplicateTrackException extends RuntimeException {
        public DuplicateTrackException() { super("이미 플레이리스트에 있는 곡입니다."); }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(User user) {
        return playlistRepository.findByUser_IdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(this::summary)
                .toList();
    }

    @Transactional
    public Map<String, Object> create(User user, String name) {
        if (playlistRepository.countByUser_Id(user.getId()) >= MAX_PLAYLISTS) {
            throw new IllegalArgumentException("플레이리스트는 최대 " + MAX_PLAYLISTS + "개까지 만들 수 있습니다.");
        }
        Playlist saved = playlistRepository.save(new Playlist(user, cleanName(name)));
        return summary(saved);
    }

    @Transactional
    public Map<String, Object> rename(User user, Long playlistId, String name) {
        Playlist p = owned(user, playlistId);
        p.rename(cleanName(name));
        return summary(p);
    }

    @Transactional
    public void delete(User user, Long playlistId) {
        Playlist p = owned(user, playlistId);
        playlistItemRepository.deleteByPlaylistId(p.getId());
        playlistRepository.delete(p);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(User user, Long playlistId) {
        Playlist p = owned(user, playlistId);
        List<Map<String, Object>> tracks = playlistItemRepository.findByPlaylist_IdOrderByPositionAsc(p.getId())
                .stream()
                .map(item -> {
                    Map<String, Object> t = new HashMap<>();
                    t.put("itemId", item.getId());
                    t.put("music", new MusicDto.Response(item.getMusic()));
                    return t;
                })
                .toList();
        Map<String, Object> body = summary(p);
        body.put("tracks", tracks);
        return body;
    }

    @Transactional
    public Map<String, Object> addTrack(User user, Long playlistId, Long musicId) {
        Playlist p = owned(user, playlistId);
        if (musicId == null) throw new IllegalArgumentException("musicId 가 필요합니다.");
        Music music = musicRepository.findById(musicId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 곡입니다."));
        if (playlistItemRepository.existsByPlaylist_IdAndMusic_Id(p.getId(), musicId)) {
            throw new DuplicateTrackException();
        }
        if (playlistItemRepository.countByPlaylist_Id(p.getId()) >= MAX_TRACKS) {
            throw new IllegalArgumentException("한 플레이리스트에는 최대 " + MAX_TRACKS + "곡까지 담을 수 있습니다.");
        }
        int position = playlistItemRepository.findMaxPosition(p.getId()) + 1;
        playlistItemRepository.save(new PlaylistItem(p, music, position));
        p.touch();
        return summary(p);
    }

    @Transactional
    public void removeTrack(User user, Long playlistId, Long itemId) {
        Playlist p = owned(user, playlistId);
        PlaylistItem item = playlistItemRepository.findById(itemId)
                .filter(i -> i.getPlaylist().getId().equals(p.getId()))
                .orElseThrow(() -> new IllegalArgumentException("플레이리스트에 없는 곡입니다."));
        playlistItemRepository.delete(item);
        p.touch();
    }

    private Playlist owned(User user, Long playlistId) {
        Playlist p = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new IllegalArgumentException("플레이리스트를 찾을 수 없습니다."));
        if (!p.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("내 플레이리스트만 볼 수 있습니다.");
        }
        return p;
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("플레이리스트 이름을 입력해주세요.");
        return n.length() > MAX_NAME_LENGTH ? n.substring(0, MAX_NAME_LENGTH) : n;
    }

    private Map<String, Object> summary(Playlist p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("trackCount", playlistItemRepository.countByPlaylist_Id(p.getId()));
        m.put("coverUrl", playlistItemRepository.findFirstByPlaylist_IdOrderByPositionAsc(p.getId())
                .map(i -> i.getMusic().getThumbnailUrl())
                .orElse(null));
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }
}
