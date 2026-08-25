package com.example.music.service;

import com.example.music.dto.MusicDto;
import com.example.music.dto.YouTubeVideoDto;
import com.example.music.entity.Music;
import com.example.music.repository.MusicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MusicService {

    private final MusicRepository musicRepository;
    private final YouTubeApiService youTubeApiService;

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
}