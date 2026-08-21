package com.example.music.service;

import com.example.music.dto.YouTubeVideoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class YouTubeApiService {

    @Value("${youtube.api.key}")
    private String apiKey;

    public YouTubeVideoDto getVideoInfo(String videoId) throws Exception {

        String url =
                "https://www.googleapis.com/youtube/v3/videos"
                        + "?part=snippet"
                        + "&id=" + videoId
                        + "&key=" + apiKey;

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode root =
                objectMapper.readTree(response.body());

        JsonNode items = root.get("items");

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("YouTube 영상을 찾을 수 없습니다.");
        }

        JsonNode snippet =
                items.get(0).get("snippet");

        String title =
                snippet.get("title").asText();

        String artist =
                snippet.get("channelTitle").asText();

        String thumbnailUrl =
                snippet
                        .get("thumbnails")
                        .get("high")
                        .get("url")
                        .asText();

        return new YouTubeVideoDto(
                videoId,
                title,
                artist,
                thumbnailUrl
        );
    }
}