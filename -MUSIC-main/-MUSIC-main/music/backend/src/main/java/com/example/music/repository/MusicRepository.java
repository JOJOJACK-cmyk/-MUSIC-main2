package com.example.music.repository;

import com.example.music.entity.Music;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MusicRepository extends JpaRepository<Music, Long> {

    // 현재 트렌딩 순위가 매겨진 음원 (재동기화 시 이전 순위를 비우기 위해 로드)
    List<Music> findByTrendingRankIsNotNull();

    long countByGenre(String genre);

    // 유튜브 Data API DB 캐시 조회를 위한 메서드
    Optional<Music> findByYoutubeVideoId(String youtubeVideoId);

    // 유튜브 영상 중복 검사를 위한 메서드
    boolean existsByYoutubeVideoId(String youtubeVideoId);

    // 💡 엑셀/DB 전체 대상 키워드 검색 메서드 (제목 또는 아티스트에 키워드 포함, 대소문자 무시)
    List<Music> findByTitleContainingIgnoreCaseOrArtistContainingIgnoreCase(String title, String artist);

    // 💡 재생시간/업로드일/조회수가 아직 채워지지 않은 음원 (메타데이터 백필 대상)
    List<Music> findByDurationSecondsIsNullOrPublishedAtIsNullOrViewCountIsNull();

    // 💡 조회수(유튜브) 높은 순 - 청취기록이 없을 때 차트 폴백 및 카테고리 인기곡 정렬용
    List<Music> findTop100ByViewCountIsNotNullOrderByViewCountDesc();
}