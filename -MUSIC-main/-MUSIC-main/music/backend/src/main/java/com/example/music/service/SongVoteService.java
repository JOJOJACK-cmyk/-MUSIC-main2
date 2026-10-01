package com.example.music.service;

import com.example.music.dto.SongVoteDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 라이브 투표.
 *  - 곡 목록(옵션)은 스트리머가 등록한다.
 *  - 시청자는 번호로만 투표한다 (채팅 "투표1" 또는 투표 패널 클릭). 1인 1표, 변경 가능.
 *
 * Redis 키 (broadcastId 기준)
 *   broadcast:{id}:poll:options        LIST   옵션 텍스트(등록 순서)
 *   broadcast:{id}:poll:votes          HASH   { indexStr -> count }
 *   broadcast:{id}:poll:musicIds       HASH   { indexStr -> musicId }  카탈로그에서 고른 옵션만
 *   broadcast:{id}:poll:voter:{who}    STRING 이 시청자가 고른 index
 */
@Slf4j
@Service
public class SongVoteService {

    private final StringRedisTemplate redis;
    private static final Duration TTL = Duration.ofHours(12);

    public SongVoteService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String optionsKey(Long b) { return "broadcast:" + b + ":poll:options"; }
    private String votesKey(Long b)   { return "broadcast:" + b + ":poll:votes"; }
    private String musicIdsKey(Long b) { return "broadcast:" + b + ":poll:musicIds"; }

    /** 투표 옵션 하나. musicId 가 있으면 1위 확정 시 제목 재검색 없이 그 곡을 바로 재생한다. */
    public record PollOption(String title, Long musicId) {
        public static PollOption text(String title) { return new PollOption(title, null); }
    }
    private String voterKey(Long b, String who) { return "broadcast:" + b + ":poll:voter:" + who; }

    // ── 스트리머: 옵션 관리 ──────────────────────────────────────────

    /** 곡 목록(제목만)을 통째로 교체하고 표를 초기화한다. */
    public void setOptions(Long broadcastId, List<String> options) {
        List<PollOption> opts = new ArrayList<>();
        if (options != null) for (String t : options) opts.add(PollOption.text(t));
        setPollOptions(broadcastId, opts);
    }

    /** 곡 목록을 통째로 교체하고 표를 초기화한다. (최대 10개) */
    public void setPollOptions(Long broadcastId, List<PollOption> options) {
        clearVotesOnly(broadcastId);
        redis.delete(optionsKey(broadcastId));
        redis.delete(musicIdsKey(broadcastId));
        if (options == null) return;

        List<PollOption> clean = new ArrayList<>();
        for (PollOption o : options) {
            if (o == null || o.title() == null) continue;
            String t = o.title().trim();
            if (!t.isEmpty() && clean.size() < 10) clean.add(new PollOption(t, o.musicId()));
        }
        if (clean.isEmpty()) return;

        String key = optionsKey(broadcastId);
        for (int i = 0; i < clean.size(); i++) {
            redis.opsForList().rightPush(key, clean.get(i).title());
            if (clean.get(i).musicId() != null) {
                redis.opsForHash().put(musicIdsKey(broadcastId), String.valueOf(i), String.valueOf(clean.get(i).musicId()));
            }
        }
    }

    /** 곡 하나 추가. */
    public void addOption(Long broadcastId, String option) {
        if (option == null || option.isBlank()) return;
        Long size = redis.opsForList().size(optionsKey(broadcastId));
        if (size != null && size >= 10) return;
        redis.opsForList().rightPush(optionsKey(broadcastId), option.trim());
    }

    /** index 곡 삭제 후 표 초기화(번호가 밀리므로). */
    public void removeOption(Long broadcastId, int index) {
        List<SongVoteDto> poll = getPoll(broadcastId);
        if (index < 0 || index >= poll.size()) return;
        poll.remove(index);
        List<PollOption> opts = new ArrayList<>();
        for (SongVoteDto d : poll) opts.add(new PollOption(d.getSongTitle(), d.getMusicId()));
        setPollOptions(broadcastId, opts);
    }

    // ── 시청자: 투표 ────────────────────────────────────────────────

    /**
     * 번호(0-base)로 투표. 한 사람(voterId)당 딱 한 번만 반영된다.
     * (채팅 "투표N" 과 투표 버튼이 같은 voterId 를 쓰므로 합쳐서 1표)
     * @return true = 이번에 표가 반영됨 / false = 이미 투표했거나 잘못된 번호
     */
    public boolean vote(Long broadcastId, String voterId, int index) {
        if (voterId == null || voterId.isBlank()) return false;
        String vid = voterId.trim();
        if (vid.isEmpty() || vid.equals("게스트")) return false; // 비로그인은 투표 불가

        int total = getOptions(broadcastId).size();
        if (total == 0 || index < 0 || index >= total) return false;

        String vk = voterKey(broadcastId, vid);
        // SETNX 로 "투표 기록"을 원자적으로 선점 — 동시에 두 번 눌러도 1표만 반영된다
        Boolean first = redis.opsForValue().setIfAbsent(vk, String.valueOf(index), TTL);
        if (!Boolean.TRUE.equals(first)) return false; // 이미 투표함 — 1인 1표

        redis.opsForHash().increment(votesKey(broadcastId), String.valueOf(index), 1);
        return true;
    }

    /** 이 사람이 이 방송에서 이미 투표했는지 (프론트 표시용) */
    public Integer votedIndex(Long broadcastId, String voterId) {
        if (voterId == null || voterId.isBlank()) return null;
        String v = redis.opsForValue().get(voterKey(broadcastId, voterId.trim()));
        try { return v == null ? null : Integer.valueOf(v); } catch (Exception e) { return null; }
    }

    // ── 조회 ──────────────────────────────────────────────────────

    public List<String> getOptions(Long broadcastId) {
        List<String> l = redis.opsForList().range(optionsKey(broadcastId), 0, -1);
        return l != null ? new ArrayList<>(l) : new ArrayList<>();
    }

    /** 옵션 순서대로 (번호 고정) 득표수와 함께 반환. */
    public List<SongVoteDto> getPoll(Long broadcastId) {
        List<String> opts = getOptions(broadcastId);
        List<SongVoteDto> out = new ArrayList<>();
        if (opts.isEmpty()) return out;
        Map<Object, Object> votes = redis.opsForHash().entries(votesKey(broadcastId));
        Map<Object, Object> musicIds = redis.opsForHash().entries(musicIdsKey(broadcastId));
        for (int i = 0; i < opts.size(); i++) {
            Object c = votes.get(String.valueOf(i));
            long count = 0;
            try { count = c == null ? 0 : Long.parseLong(String.valueOf(c)); } catch (Exception ignore) {}
            Long musicId = null;
            Object mid = musicIds.get(String.valueOf(i));
            try { musicId = mid == null ? null : Long.valueOf(String.valueOf(mid)); } catch (Exception ignore) {}
            out.add(new SongVoteDto(i, opts.get(i), Math.max(0, count), musicId));
        }
        return out;
    }

    /** 하위호환: 예전 컨트롤러가 부르던 이름 — 득표순 정렬로 반환. */
    public List<SongVoteDto> getTopSongRankings(Long broadcastId, int limit) {
        List<SongVoteDto> poll = getPoll(broadcastId);
        poll.sort((a, b) -> Long.compare(b.getVoteCount(), a.getVoteCount()));
        return limit > 0 && poll.size() > limit ? poll.subList(0, limit) : poll;
    }

    /** 1위 항목 (동점이면 낮은 번호). 옵션이 없으면 null. */
    public SongVoteDto getTop1(Long broadcastId) {
        SongVoteDto best = null;
        for (SongVoteDto d : getPoll(broadcastId)) {
            if (best == null || d.getVoteCount() > best.getVoteCount()) best = d;
        }
        return best;
    }

    /** 1위 곡 제목 (동점이면 낮은 번호). */
    public String getTop1Song(Long broadcastId) {
        SongVoteDto best = getTop1(broadcastId);
        return best != null ? best.getSongTitle() : "등록된 신청곡이 없습니다.";
    }

    private void clearVotesOnly(Long broadcastId) {
        redis.delete(votesKey(broadcastId));
        Set<String> voterKeys = redis.keys("broadcast:" + broadcastId + ":poll:voter:*");
        if (voterKeys != null && !voterKeys.isEmpty()) redis.delete(voterKeys);
    }

    /** 표만 초기화 (곡 목록은 유지). */
    public void resetVotes(Long broadcastId) {
        clearVotesOnly(broadcastId);
    }

    /** 방송 종료 시 전체 정리. */
    public void clearBroadcastVotes(Long broadcastId) {
        clearVotesOnly(broadcastId);
        redis.delete(optionsKey(broadcastId));
        redis.delete(musicIdsKey(broadcastId));
        log.info("방송 투표 Redis 정리 완료: broadcastId={}", broadcastId);
    }
}
