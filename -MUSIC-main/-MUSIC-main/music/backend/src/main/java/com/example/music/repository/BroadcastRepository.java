package com.example.music.repository;

import com.example.music.entity.Broadcast;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BroadcastRepository
        extends JpaRepository<Broadcast, Long> {

    Optional<Broadcast> findByUser_Id(Long userId);

    Optional<Broadcast> findByStreamKey(String streamKey);

    List<Broadcast> findByStreamKeyIn(
            Collection<String> streamKeys
    );

    List<Broadcast> findByStatusIgnoreCase(String status);

    @EntityGraph(attributePaths = "user")
    Optional<Broadcast> findWithUserById(Long id);
}