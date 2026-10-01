package com.example.music.repository;

import com.example.music.entity.PlaylistItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, Long> {

    @EntityGraph(attributePaths = "music")
    List<PlaylistItem> findByPlaylist_IdOrderByPositionAsc(Long playlistId);

    Optional<PlaylistItem> findFirstByPlaylist_IdOrderByPositionAsc(Long playlistId);

    boolean existsByPlaylist_IdAndMusic_Id(Long playlistId, Long musicId);

    long countByPlaylist_Id(Long playlistId);

    @Query("select coalesce(max(i.position), -1) from PlaylistItem i where i.playlist.id = :playlistId")
    int findMaxPosition(@Param("playlistId") Long playlistId);

    @Modifying
    @Query("delete from PlaylistItem i where i.playlist.id = :playlistId")
    void deleteByPlaylistId(@Param("playlistId") Long playlistId);
}
