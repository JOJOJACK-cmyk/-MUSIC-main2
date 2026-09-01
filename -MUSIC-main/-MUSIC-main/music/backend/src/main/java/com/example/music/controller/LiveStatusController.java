package com.example.music.controller;

import com.example.music.dto.LiveStatusResponse;
import com.example.music.service.SrsLiveStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveStatusController {

    private final SrsLiveStatusService srsLiveStatusService;

    @GetMapping("/status")
    public LiveStatusResponse getLiveStatus() {
        return srsLiveStatusService.getLiveStatus();
    }
}