package com.example.music.controller;

import com.example.music.dto.SrsCallbackDto;
import com.example.music.service.BroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SRS(미디어 서버)가 실제 RTMP publish/unpublish 이벤트 발생 시 호출하는 웹훅 수신 컨트롤러.
 * 로그인 사용자가 아니라 SRS 서버(같은 호스트)에서만 호출되므로 SecurityConfig에서 permitAll 처리함.
 * 외부에서는 호출할 수 없도록 Caddy 에서 /api/broadcast/srs/* 를 차단한다 (infra/caddy/Caddyfile).
 *
 * SRS 쪽 설정(srs.conf)의 http_hooks.on_publish / on_unpublish 가 이 엔드포인트를 바라보게 되어 있음.
 */
@Slf4j
@RestController
@RequestMapping("/api/broadcast/srs")
@RequiredArgsConstructor
public class SrsCallbackController {

    private final BroadcastService broadcastService;

    /**
     * on_publish: 실제 방송 송출이 시작될 때 SRS가 호출
     * on_unpublish: 실제 방송 송출이 끊겼을 때 SRS가 호출 (OBS 종료, 네트워크 끊김 등 포함)
     */
    @PostMapping("/callback")
    public ResponseEntity<String> handleCallback(@RequestBody SrsCallbackDto payload) {

        log.info(
                "[SRS Callback] action={}, app={}, stream={}",
                payload.getAction(), payload.getApp(), payload.getStream()
        );

        if ("on_publish".equals(payload.getAction())) {
            // SRS 는 응답 바디가 "0" 일 때만 송출을 허용한다. 키가 틀리면 거부해서
            // 남의 방송 주소로 송출(방송 탈취)하는 것을 막는다.
            boolean allowed = broadcastService.handleStreamPublished(payload.getStream(), payload.getParam());
            return allowed
                    ? ResponseEntity.ok("0")
                    : ResponseEntity.status(HttpStatus.FORBIDDEN).body("1");
        } else if ("on_unpublish".equals(payload.getAction())) {
            broadcastService.handleStreamUnpublished(payload.getStream());
        }

        return ResponseEntity.ok("0");
    }
}
