package com.example.music.service;

import com.example.music.dto.MusicDto;
import com.example.music.entity.Music;
import com.example.music.repository.MusicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.example.music.dto.YouTubeVideoDto;

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

        music.update(request.getTitle(), request.getArtist(), request.getThumbnailUrl());
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

        // 이미 DB에 등록된 영상인지 확인
        musicRepository.findByYoutubeVideoId(videoId).ifPresent(m -> {
            throw new IllegalArgumentException("이미 등록된 음원(영상)입니다.");
        });

        // YouTube API에서 영상 정보 가져오기
        YouTubeVideoDto video =
                youTubeApiService.getVideoInfo(videoId);

        // YouTube 정보를 Music Entity로 변환
        Music music = Music.builder()
                .youtubeVideoId(video.getYoutubeVideoId())
                .title(video.getTitle())
                .artist(video.getArtist())
                .thumbnailUrl(video.getThumbnailUrl())
                .build();

        // DB 저장
        Music savedMusic =
                musicRepository.save(music);

        return new MusicDto.Response(savedMusic);
    }
}