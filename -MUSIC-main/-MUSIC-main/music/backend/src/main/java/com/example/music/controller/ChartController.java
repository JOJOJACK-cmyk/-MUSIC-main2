package com.example.music.controller;

import com.example.music.dto.MusicRankingDto;
import com.example.music.service.ChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
public class ChartController {

    private final ChartService chartService;

    @GetMapping("/realtime")
    public ResponseEntity<List<MusicRankingDto>> getRealTimeChart() {
        List<MusicRankingDto> top100 = chartService.getRealTimeTop100();
        return ResponseEntity.ok(top100);
    }
}