package com.example.music.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.cloudfront.domain}")
    private String cloudFrontDomain;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    // 업로드 허용: 이미지(프로필/배너/썸네일)만, 5MB 이하
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    public String uploadFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("파일 크기는 5MB 이하만 업로드할 수 있습니다.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String ext = ALLOWED_TYPES.get(contentType);
        if (ext == null) {
            throw new IllegalArgumentException("이미지 파일(jpg, png, webp, gif)만 업로드할 수 있습니다.");
        }

        // 원본 파일명은 쓰지 않는다 (경로 문자·특수문자로 인한 키 오염 방지). 확장자는 MIME 기준.
        String key = "uploads/" + UUID.randomUUID() + "." + ext;

        try {

            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType)
                            .build();

            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            file.getInputStream(),
                            file.getSize()
                    )
            );

            return key;

        } catch (IOException e) {

            throw new RuntimeException(
                    "S3 파일 업로드 실패",
                    e
            );
        }
    }

    public String getCloudFrontUrl(String key) {
        return cloudFrontDomain + "/" + key;
    }
}