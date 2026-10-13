package com.example.music;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MusicApplication extends SpringBootServletInitializer {

    // 외부 Tomcat(WAR 배포)에서 애플리케이션을 기동하기 위한 진입점
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(MusicApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(MusicApplication.class, args);
    }

}
