package com.example.music.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * SRS(미디어 서버)가 on_publish / on_unpublish 시점에 보내는 웹훅 페이로드.
 * SRS가 실제로 보내는 필드는 더 많지만(client_id, ip, vhost, tcUrl, param 등),
 * 지금 필요한 것만 매핑함.
 */
@Getter
@Setter
public class SrsCallbackDto {

    // "on_publish" | "on_unpublish" 등
    private String action;

    // vhost 하위 app 이름 (여기선 "live" 고정)
    private String app;

    // 스트림 키 (Broadcast.streamKey와 매칭됨)
    private String stream;
}