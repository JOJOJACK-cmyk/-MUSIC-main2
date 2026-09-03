package com.example.music.repository;

import com.example.music.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollower_IdAndFollowing_Id(Long followerId, Long followingId);

    Optional<Follow> findByFollower_IdAndFollowing_Id(Long followerId, Long followingId);

    List<Follow> findByFollower_IdOrderByCreatedAtDesc(Long followerId);

    List<Follow> findByFollowing_Id(Long followingId);

    long countByFollowing_Id(Long followingId);

    long countByFollower_Id(Long followerId);
}
