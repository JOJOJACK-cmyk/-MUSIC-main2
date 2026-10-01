package com.example.music.service;

import com.example.music.dto.SongVoteDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SongVoteServiceTest {

    private static final Long BROADCAST_ID = -9999L; // 실제 방송과 겹치지 않는 테스트용 ID

    @Autowired
    private SongVoteService songVoteService;

    @AfterEach
    void cleanup() {
        songVoteService.clearBroadcastVotes(BROADCAST_ID);
    }

    @Test
    void 카탈로그_곡은_musicId_가_유지되고_1위로_선택된다() {
        songVoteService.setPollOptions(BROADCAST_ID, List.of(
                SongVoteService.PollOption.text("직접 입력한 곡"),
                new SongVoteService.PollOption("카탈로그 곡 - 가수", 42L)
        ));

        assertThat(songVoteService.vote(BROADCAST_ID, "u:1", 1)).isTrue();
        assertThat(songVoteService.vote(BROADCAST_ID, "u:1", 0)).isFalse(); // 1인 1표
        assertThat(songVoteService.vote(BROADCAST_ID, "u:2", 1)).isTrue();

        List<SongVoteDto> poll = songVoteService.getPoll(BROADCAST_ID);
        assertThat(poll).hasSize(2);
        assertThat(poll.get(0).getMusicId()).isNull();
        assertThat(poll.get(1).getMusicId()).isEqualTo(42L);
        assertThat(poll.get(1).getVoteCount()).isEqualTo(2);

        SongVoteDto top = songVoteService.getTop1(BROADCAST_ID);
        assertThat(top.getMusicId()).isEqualTo(42L);
        assertThat(top.getSongTitle()).isEqualTo("카탈로그 곡 - 가수");
    }

    @Test
    void 옵션_삭제_후에도_musicId_가_올바른_번호에_남는다() {
        songVoteService.setPollOptions(BROADCAST_ID, List.of(
                SongVoteService.PollOption.text("A"),
                new SongVoteService.PollOption("B", 7L)
        ));
        songVoteService.removeOption(BROADCAST_ID, 0);

        List<SongVoteDto> poll = songVoteService.getPoll(BROADCAST_ID);
        assertThat(poll).hasSize(1);
        assertThat(poll.get(0).getSongTitle()).isEqualTo("B");
        assertThat(poll.get(0).getMusicId()).isEqualTo(7L);
    }
}
