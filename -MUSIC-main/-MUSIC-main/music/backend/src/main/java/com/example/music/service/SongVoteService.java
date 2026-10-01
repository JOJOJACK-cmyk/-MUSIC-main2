package com.example.music.service;

import com.example.music.dto.SongVoteDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 라이브 투표.
 *  - 곡 목록(옵션)은 스트리머가 등록한다.
 *  - 시청자는 번호로만 투표한다 (채팅 "투표1" 또는 투표 패널 클릭). 1인 1표, 변경 가능.
 *
 * Redis 키 (broadcastId 기준)
 *   broadcast:{id}:poll:options        LIST   옵션 텍스트(등록 순서)
 *   broadcast:{id}:poll:votes          HASH   { indexStr -> count }
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
    private String voterKey(Long b, String who) { return "broadcast:" + b + ":poll:voter:" + who; }

    // ── 스트리머: 옵션 관리 ──────────────────────────────────────────

    /** 곡 목록을 통째로 교체하고 표를 초기화한다. */
    public void setOptions(Long broadcastId, List<String> options) {
        clearVotesOnly(broadcastId);
        redis.delete(optionsKey(broadcastId));
        if (options == null) return;

        List<String> clean = new ArrayList<>();
        for (String s : options) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty() && clean.size() < 10) clean.add(t);
        }
        if (clean.isEmpty()) return;

        String key = optionsKey(broadcastId);
        for (String opt : clean) {
            redis.opsForList().rightPush(key, opt);
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
        List<String> opts = getOptions(broadcastId);
        if (index < 0 || index >= opts.size()) return;
        opts.remove(index);
        setOptions(broadcastId, opts);
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
        for (int i = 0; i < opts.size(); i++) {
            Object c = redis.opsForHash().get(votesKey(broadcastId), String.valueOf(i));
            long count = 0;
            try { count = c == null ? 0 : Long.parseLong(String.valueOf(c)); } catch (Exception ignore) {}
            out.add(new SongVoteDto(i, opts.get(i), Math.max(0, count)));
        }
        return out;
    }

    /** 하위호환: 예전 컨트롤러가 부르던 이름 — 득표순 정렬로 반환. */
    public List<SongVoteDto> getTopSongRankings(Long broadcastId, int limit) {
        List<SongVoteDto> poll = getPoll(broadcastId);
        poll.sort((a, b) -> Long.compare(b.getVoteCount(), a.getVoteCount()));
        return limit > 0 && poll.size() > limit ? poll.subList(0, limit) : poll;
    }

    /** 1위 곡 제목 (동점이면 낮은 번호). */
    public String getTop1Song(Long broadcastId) {
        String best = null;
        long bestCount = -1;
        for (SongVoteDto d : getPoll(broadcastId)) {
            if (d.getVoteCount() > bestCount) { bestCount = d.getVoteCount(); best = d.getSongTitle(); }
        }
        return best != null ? best : "등록된 신청곡이 없습니다.";
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
        log.info("방송 투표 Redis 정리 완료: broadcastId={}", broadcastId);
    }
}
