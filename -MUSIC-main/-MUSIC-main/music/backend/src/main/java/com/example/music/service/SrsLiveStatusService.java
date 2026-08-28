package com.example.music.service;

import com.example.music.dto.LiveStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class SrsLiveStatusService {

    private static final String SRS_API = "http://127.0.0.1:1985";
    private static final String LIVE_APP = "live";
    private static final String STREAM_NAME = "livestream";

    private final JsonMapper jsonMapper;

    private final RestClient restClient =
            RestClient.create(SRS_API);

    public LiveStatusResponse getLiveStatus() {

        try {
            String response = restClient
                    .get()
                    .uri("/api/v1/streams/")
                    .retrieve()
                    .body(String.class);

            JsonNode root = jsonMapper.readTree(response);
            JsonNode streams = root.path("streams");

            for (JsonNode stream : streams) {

                String app = stream.path("app").asText();
                String name = stream.path("name").asText();

                boolean publishing = stream
                        .path("publish")
                        .path("active")
                        .asBoolean(false);

                if (LIVE_APP.equals(app)
                        && STREAM_NAME.equals(name)
                        && publishing) {

                    return new LiveStatusResponse(
                            true,
                            true,
                            "LIVE"
                    );
                }
            }

            return new LiveStatusResponse(
                    true,
                    false,
                    "OFFLINE"
            );

        } catch (Exception e) {

            return new LiveStatusResponse(
                    false,
                    false,
                    "SRS_DOWN"
            );
        }
    }
}