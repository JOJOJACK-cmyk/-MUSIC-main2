package com.example.music.dto;

/**
 * 라이브 투표 한 항목.
 *   index    - 투표 번호(0-base). 프론트/채팅은 "투표1" = index 0.
 *   songTitle- 곡 제목 (스트리머가 등록)
 *   voteCount- 득표수
 *   musicId  - 카탈로그 곡 ID (스트리머가 검색해서 고른 경우). 직접 입력한 제목이면 null
 */
public class SongVoteDto {
    private int index;
    private String songTitle;
    private long voteCount;
    private Long musicId;

    public SongVoteDto(String songTitle, long voteCount) {
        this(-1, songTitle, voteCount);
    }

    public SongVoteDto(int index, String songTitle, long voteCount) {
        this(index, songTitle, voteCount, null);
    }

    public SongVoteDto(int index, String songTitle, long voteCount, Long musicId) {
        this.index = index;
        this.songTitle = songTitle;
        this.voteCount = voteCount;
        this.musicId = musicId;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public String getSongTitle() { return songTitle; }
    public void setSongTitle(String songTitle) { this.songTitle = songTitle; }
    public long getVoteCount() { return voteCount; }
    public void setVoteCount(long voteCount) { this.voteCount = voteCount; }
    public Long getMusicId() { return musicId; }
    public void setMusicId(Long musicId) { this.musicId = musicId; }
}
