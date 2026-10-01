package com.example.music.repository;

import com.example.music.entity.Music;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // 💡 띄어쓰기 무시 검색 — 제목/아티스트의 공백을 모두 지우고 비교 (kwNoSpaces 도 공백 제거해서 넘길 것)
    @Query("select m from Music m where "
            + "lower(replace(m.title, ' ', '')) like lower(concat('%', :kwNoSpaces, '%')) "
            + "or lower(replace(m.artist, ' ', '')) like lower(concat('%', :kwNoSpaces, '%'))")
    List<Music> findByTitleOrArtistIgnoringSpaces(@Param("kwNoSpaces") String kwNoSpaces);

    // 💡 재생시간/업로드일/조회수가 아직 채워지지 않은 음원 (메타데이터 백필 대상)
    List<Music> findByDurationSecondsIsNullOrPublishedAtIsNullOrViewCountIsNull();

    // 💡 조회수(유튜브) 높은 순 - 청취기록이 없을 때 차트 폴백 및 카테고리 인기곡 정렬용
    List<Music> findTop100ByViewCountIsNotNullOrderByViewCountDesc();

    // 💡 재생시간이 비슷한 곡 후보 (수집 시점 "이미 같은 곡(다른 videoId) 있는지" 판별용 예비 필터)
    List<Music> findByDurationSecondsBetween(Long min, Long max);
}