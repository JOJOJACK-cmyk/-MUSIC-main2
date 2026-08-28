package com.example.music.dto;

public class SongVoteDto {
    private String songTitle; // 곡 제목 (예: "Ditto")
    private long voteCount;   // 득표수

    // Getters, Setters, Constructors
    public SongVoteDto(String songTitle, long voteCount) {
        this.songTitle = songTitle;
        this.voteCount = voteCount;
    }

    public String getSongTitle() { return songTitle; }
    public void setSongTitle(String songTitle) { this.songTitle = songTitle; }
    public long getVoteCount() { return voteCount; }
    public void setVoteCount(long voteCount) { this.voteCount = voteCount; }
}
