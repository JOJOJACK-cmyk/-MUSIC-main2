package com.example.music.controller;

import com.example.music.service.S3Service;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/s3")
public class S3Controller {

    private final S3Service s3Service;

    public S3Controller(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, String>> upload(
            @RequestPart("file") MultipartFile file
    ) {

        String key = s3Service.uploadFile(file);

        return ResponseEntity.ok(
                Map.of(
                        "key", key,
                        "message", "S3 파일 업로드 성공"
                )
        );
    }
}