package com.example.music.repository;

import com.example.music.entity.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    long countByUser_Id(Long userId);
}
