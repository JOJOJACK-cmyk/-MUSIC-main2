package com.example.music.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }


    public String uploadFile(MultipartFile file) {

        // 원래 파일 이름
        String originalFilename = file.getOriginalFilename();

        // 파일 이름 중복 방지를 위한 UUID
        String fileName =
                UUID.randomUUID()
                        + "_"
                        + originalFilename;

        // S3 안에서 저장될 위치
        String key =
                "uploads/" + fileName;


        try {

            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(file.getContentType())
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
}