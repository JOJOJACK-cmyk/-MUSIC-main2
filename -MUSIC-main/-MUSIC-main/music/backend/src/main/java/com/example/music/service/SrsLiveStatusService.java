package com.example.music.service;

import com.example.music.dto.LiveStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SrsLiveStatusService {

    private static final String LIVE_APP = "live";

    // 기존 단일 테스트용 스트림
    private static final String STREAM_NAME = "livestream";

    private final JsonMapper jsonMapper;

    // [수정] SRS API 주소를 환경별로 분리.
    //  - 로컬(IntelliJ 직접 실행): 기본값 http://127.0.0.1:1985
    //  - 서버(docker compose):    srs.api.base-url=http://srs:1985
    // RestClient 를 필드 초기화 시점에 만들면 @Value 주입 전이라 값이 비므로
    // 반드시 생성자에서 만들어야 한다.
    private final RestClient restClient;

    public SrsLiveStatusService(
            JsonMapper jsonMapper,
            @Value("${srs.api.base-url:http://127.0.0.1:1985}") String srsApiBaseUrl
    ) {
        this.jsonMapper = jsonMapper;
        this.restClient = RestClient.create(srsApiBaseUrl);
    }

    /**
     * 기존 단일 방송 상태 확인
     */
    public LiveStatusResponse getLiveStatus() {

        try {
            List<String> activeStreams = loadActiveStreamKeys();

            boolean live = activeStreams.contains(STREAM_NAME);

            return new LiveStatusResponse(
                    true,
                    live,
                    live ? "LIVE" : "OFFLINE"
            );

        } catch (Exception e) {

            return new LiveStatusResponse(
                    false,
                    false,
                    "SRS_DOWN"
            );
        }
    }

    /**
     * 현재 SRS에서 실제 송출 중인 모든 streamKey 조회
     */
    public List<String> getActiveStreamKeys() {

        try {
            return loadActiveStreamKeys();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "SRS 서버에 연결할 수 없습니다.",
                    e
            );
        }
    }

    /**
     * SRS API 실제 조회
     */
    private List<String> loadActiveStreamKeys() throws Exception {

        String response = restClient
                .get()
                .uri("/api/v1/streams/")
                .retrieve()
                .body(String.class);

        JsonNode root = jsonMapper.readTree(response);
        JsonNode streams = root.path("streams");

        List<String> activeStreams = new ArrayList<>();

        for (JsonNode stream : streams) {

            String app = stream.path("app").asText();
            String name = stream.path("name").asText();

            boolean publishing = stream
                    .path("publish")
                    .path("active")
                    .asBoolean(false);

            if (LIVE_APP.equals(app) && publishing) {
                activeStreams.add(name);
            }
        }

        return activeStreams;
    }

    public Map<String, Integer> getActiveStreamsWithViewerCount() {

        try {
            String response = restClient
                    .get()
                    .uri("/api/v1/streams/")
                    .retrieve()
                    .body(String.class);

            JsonNode root = jsonMapper.readTree(response);
            JsonNode streams = root.path("streams");

            Map<String, Integer> result = new HashMap<>();

            for (JsonNode stream : streams) {

                String app = stream.path("app").asText();
                String name = stream.path("name").asText();

                boolean publishing = stream
                        .path("publish")
                        .path("active")
                        .asBoolean(false);

                if (LIVE_APP.equals(app) && publishing) {

                    int clients = stream
                            .path("clients")
                            .asInt(0);

                    // OBS 송출 연결 1개 제외
                    int viewerCount = Math.max(clients - 1, 0);

                    result.put(name, viewerCount);
                }
            }

            return result;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "SRS 서버에 연결할 수 없습니다.",
                    e
            );
        }
    }
}