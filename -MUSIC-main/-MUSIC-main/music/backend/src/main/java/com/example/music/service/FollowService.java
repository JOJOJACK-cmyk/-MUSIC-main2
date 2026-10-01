package com.example.music.service;

import com.example.music.entity.Broadcast;
import com.example.music.entity.Follow;
import com.example.music.entity.User;
import com.example.music.repository.BroadcastRepository;
import com.example.music.repository.FollowRepository;
import com.example.music.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final BroadcastRepository broadcastRepository;

    @Transactional
    public boolean toggle(User me, Long targetUserId) {
        if (me.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신은 팔로우할 수 없습니다.");
        }
        return followRepository.findByFollower_IdAndFollowing_Id(me.getId(), targetUserId)
                .map(f -> {
                    followRepository.delete(f);
                    return false;
                })
                .orElseGet(() -> {
                    User target = userRepository.findById(targetUserId)
                            .orElseThrow(() -> new IllegalArgumentException("대상 사용자를 찾을 수 없습니다."));
                    followRepository.save(Follow.builder().follower(me).following(target).build());
                    return true;
                });
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(Long followerId, Long targetUserId) {
        return followRepository.existsByFollower_IdAndFollowing_Id(followerId, targetUserId);
    }

    @Transactional(readOnly = true)
    public long followerCount(Long userId) {
        return followRepository.countByFollowing_Id(userId);
    }

    /** 내가 팔로우한 채널 목록 (+ 현재 라이브 여부) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myFollowing(Long myId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Follow f : followRepository.findByFollower_IdOrderByCreatedAtDesc(myId)) {
            User ch = f.getFollowing();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", ch.getId());
            row.put("nickname", ch.getNickname());
            row.put("profileImageUrl", ch.getProfileImageUrl());
            row.put("followerCount", followRepository.countByFollowing_Id(ch.getId()));

            Broadcast b = broadcastRepository.findByUser_Id(ch.getId()).orElse(null);
            row.put("live", b != null && "ON".equalsIgnoreCase(b.getStatus()));
            row.put("broadcastId", b != null ? b.getId() : null);
            row.put("broadcastTitle", b != null ? b.getTitle() : null);
            out.add(row);
        }
        return out;
    }

    /** 특정 채널을 팔로우한 사용자 ID 목록 (알림 타겟팅용) */
    @Transactional(readOnly = true)
    public List<Long> followerIds(Long channelUserId) {
        List<Long> ids = new ArrayList<>();
        for (Follow f : followRepository.findByFollowing_Id(channelUserId)) {
            ids.add(f.getFollower().getId());
        }
        return ids;
    }

    /** 나를 팔로우한 사용자 목록 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myFollowers(Long myId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Follow f : followRepository.findByFollowing_Id(myId)) {
            User u = f.getFollower();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("nickname", u.getNickname());
            row.put("profileImageUrl", u.getProfileImageUrl());
            out.add(row);
        }
        return out;
    }
}
