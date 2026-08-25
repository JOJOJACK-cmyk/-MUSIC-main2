package com.example.music.controller;

import com.example.music.service.MusicSnapshotService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/music-snapshot")
public class MusicSnapshotController {

    private final MusicSnapshotService musicSnapshotService;

    public MusicSnapshotController(MusicSnapshotService musicSnapshotService) {
        this.musicSnapshotService = musicSnapshotService;
    }

    @GetMapping("/all")
    public ResponseEntity<String> getAllMusicSnapshot() {

        String snapshot = musicSnapshotService.getAllMusicSnapshot();

        if (snapshot == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(snapshot);
    }
}