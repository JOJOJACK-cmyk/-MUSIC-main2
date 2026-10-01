package com.example.music.service;

import com.example.music.entity.Music;
import com.example.music.repository.LikedMusicRepository;
import com.example.music.repository.ListenLogRepository;
import com.example.music.repository.MusicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 곡 삭제는 반드시 여기로.
 * liked_music / listen_log 는 DB 외래키가 NO ACTION 이라 곡만 지우면 FK 오류가 나고,
 * 그 오류가 조회 트랜잭션(getAllMusic 등) 안에서 커밋 시점에 터지면 요청 전체가 실패한다.
 * (playlist_item 은 ON DELETE CASCADE, product.music_id 는 SET NULL 이라 DB 가 처리)
 */
@Component
@RequiredArgsConstructor
public class MusicRemover {

    private final MusicRepository musicRepository;
    private final LikedMusicRepository likedMusicRepository;
    private final ListenLogRepository listenLogRepository;

    /** 참조 기록을 먼저 지우고 곡을 삭제한다. 호출한 쪽 트랜잭션에 참여한다. */
    @Transactional
    public void remove(Music music) {
        Long id = music.getId();
        listenLogRepository.deleteByMusicId(id);
        likedMusicRepository.deleteByMusicId(id);
        musicRepository.delete(music);
    }
}
