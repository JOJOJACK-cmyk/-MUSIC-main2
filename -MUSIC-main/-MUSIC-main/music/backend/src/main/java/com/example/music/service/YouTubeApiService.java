package com.example.music.service;

import com.example.music.dto.YouTubeVideoDto;
import com.example.music.entity.Music;
import com.example.music.repository.MusicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeApiService {

    private final MusicRepository musicRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${youtube.api.key}")
    private String apiKey;

    /**
     * 비디오 ID 기준 조회 (DB 캐시 확인 -> 없으면 유튜브 Data API 호출 후 자동 캐싱)
     */
    @Transactional
    public YouTubeVideoDto getVideoInfo(String videoId) {
        // 1. DB 캐시 확인 (할당량 절약)
        Optional<Music> cachedMusic = musicRepository.findByYoutubeVideoId(videoId);
        if (cachedMusic.isPresent()) {
            log.info("[YouTube Cache Hit] DB에서 곡 정보를 반환합니다. videoId: {}", videoId);
            Music music = cachedMusic.get();
            return YouTubeVideoDto.builder()
                    .id(music.getId())
                    .youtubeVideoId(music.getYoutubeVideoId())
                    .title(music.getTitle())
                    .artist(music.getArtist())
                    .thumbnailUrl(music.getThumbnailUrl())
                    .build();
        }

        // 2. Cache Miss: YouTube Data API v3 호출
        log.info("[YouTube Cache Miss] YouTube API를 호출합니다. videoId: {}", videoId);
        try {
            String url = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=snippet"
                    + "&id=" + videoId
                    + "&key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.get("items");

            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("YouTube 영상을 찾을 수 없습니다: " + videoId);
            }

            JsonNode snippet = items.get(0).get("snippet");
            String title = snippet.get("title").asText();
            String artist = snippet.get("channelTitle").asText();

            // 썸네일 high 체크 (없을 경우 default 폴백)
            JsonNode thumbnails = snippet.get("thumbnails");
            String thumbnailUrl = "";
            if (thumbnails.has("high")) {
                thumbnailUrl = thumbnails.get("high").get("url").asText();
            } else if (thumbnails.has("default")) {
                thumbnailUrl = thumbnails.get("default").get("url").asText();
            }

            // 3. DB에 캐싱 저장
            Music newMusic = Music.builder()
                    .youtubeVideoId(videoId)
                    .title(title)
                    .artist(artist)
                    .thumbnailUrl(thumbnailUrl)
                    .build();
            Music savedMusic = musicRepository.save(newMusic);

            return YouTubeVideoDto.builder()
                    .id(savedMusic.getId())
                    .youtubeVideoId(videoId)
                    .title(title)
                    .artist(artist)
                    .thumbnailUrl(thumbnailUrl)
                    .build();

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("YouTube API 호출 중 오류 발생", e);
            throw new RuntimeException("YouTube API 연동 실패: " + e.getMessage());
        }
    }

    /**
     * 💡 유튜브 플레이리스트 ID를 받아 내부의 영상 목록을 일괄 조회하고 DB에 자동 동기화
     */
    @Transactional
    public List<YouTubeVideoDto> syncPlaylist(String playlistId) {
        log.info("[YouTube Playlist Sync] 플레이리스트 동기화 시작. playlistId: {}", playlistId);
        List<YouTubeVideoDto> syncedVideos = new ArrayList<>();

        try {
            // YouTube Data API v3 - playlistItems 엔드포인트 호출
            String url = "https://www.googleapis.com/youtube/v3/playlistItems"
                    + "?part=snippet"
                    + "&maxResults=50" // 한 번에 가져올 최대 개수 (최대 50개)
                    + "&playlistId=" + playlistId
                    + "&key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.get("items");

            if (items == null || !items.isArray()) {
                log.warn("플레이리스트에 영상이 없거나 잘못된 플레이리스트 ID입니다: {}", playlistId);
                return syncedVideos;
            }

            // 플레이리스트 안의 각 영상 순회
            for (JsonNode item : items) {
                JsonNode snippet = item.get("snippet");
                if (snippet != null && snippet.has("resourceId")) {
                    JsonNode resourceId = snippet.get("resourceId");
                    if (resourceId.has("videoId")) {
                        String videoId = resourceId.get("videoId").asText();

                        try {
                            // 이미 만들어둔 단건 조회/캐싱 메서드 활용 (DB에 없으면 자동 저장됨!)
                            YouTubeVideoDto videoDto = getVideoInfo(videoId);
                            syncedVideos.add(videoDto);
                        } catch (Exception e) {
                            log.error("개별 영상 동기화 중 오류 발생 (videoId: {}): {}", videoId, e.getMessage());
                        }
                    }
                }
            }

            log.info("[YouTube Playlist Sync] 플레이리스트 동기화 완료. 총 {}곡 처리됨.", syncedVideos.size());
        } catch (Exception e) {
            log.error("YouTube 플레이리스트 API 호출 중 오류 발생", e);
            throw new RuntimeException("YouTube 플레이리스트 연동 실패: " + e.getMessage());
        }

        return syncedVideos;
    }
}