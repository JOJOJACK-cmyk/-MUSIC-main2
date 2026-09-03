package com.example.music.controller;

import com.example.music.entity.User;
import com.example.music.security.AuthenticatedUserResolver;
import com.example.music.service.FollowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Follow API", description = "채널 팔로우")
@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    /** 팔로우 토글 → {following: true/false} */
    @PostMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable Long userId, Authentication authentication) {
        User me = authenticatedUserResolver.resolveRequiredUser(authentication);
        boolean following = followService.toggle(me, userId);
        return ResponseEntity.ok(Map.of("following", following,
                "followerCount", followService.followerCount(userId)));
    }

    /** 내가 특정 채널을 팔로우 중인지 */
    @GetMapping("/status/{userId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long userId, Authentication authentication) {
        User me = authenticatedUserResolver.resolveRequiredUser(authentication);
        return ResponseEntity.ok(Map.of(
                "following", followService.isFollowing(me.getId(), userId),
                "followerCount", followService.followerCount(userId)));
    }

    /** 내가 팔로우한 채널 목록 (라이브 여부 포함) */
    @GetMapping("/following")
    public ResponseEntity<List<Map<String, Object>>> myFollowing(Authentication authentication) {
        User me = authenticatedUserResolver.resolveRequiredUser(authentication);
        return ResponseEntity.ok(followService.myFollowing(me.getId()));
    }

    /** 나를 팔로우한 사용자 목록 */
    @GetMapping("/followers")
    public ResponseEntity<List<Map<String, Object>>> myFollowers(Authentication authentication) {
        User me = authenticatedUserResolver.resolveRequiredUser(authentication);
        return ResponseEntity.ok(followService.myFollowers(me.getId()));
    }
}
