import React from 'react';
import { usePlayer } from '../context/PlayerContext';

export default function PlayerBar() {
  const {
    currentTrack,
    isPlaying,
    currentTime,
    duration,
    volume,
    isShuffle,
    isRepeat,
    togglePlay,
    handleNextTrack,
    handlePrevTrack,
    seekTime,
    setVolume,
    setIsShuffle,
    setIsRepeat,
  } = usePlayer();

  const formatTime = (seconds) => {
    const min = Math.floor(seconds / 60);
    const sec = Math.floor(seconds % 60);
    return `${min}:${sec < 10 ? '0' : ''}${sec}`;
  };

  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  const handleProgressClick = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const width = rect.width;
    seekTime((clickX / width) * 100);
  };

  const handleVolumeClick = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const width = rect.width;
    setVolume(Math.round(Math.max(0, Math.min(100, (clickX / width) * 100))));
  };

  return (
    <footer className="player-bar">
      {/* 곡 정보 */}
      <div className="player-track-info">
        <div className="track-thumb">
          {currentTrack?.thumbnailUrl ? (
            <img src={currentTrack.thumbnailUrl} alt={currentTrack.title} />
          ) : (
            <i className="fa-solid fa-music"></i>
          )}
        </div>
        <div className="track-detail">
          <div className="title" id="track-title">
            {currentTrack ? currentTrack.title : '재생 중인 곡이 없습니다'}
          </div>
          <div className="artist" id="track-artist">
            {currentTrack ? currentTrack.artist || '아티스트 미상' : 'StreamWave Music'}
          </div>
        </div>
      </div>

      {/* 플레이어 컨트롤 (중앙) */}
      <div className="player-controls">
        <div className="control-buttons">
          <button
            className={isShuffle ? 'active' : ''}
            onClick={() => setIsShuffle(!isShuffle)}
          >
            <i className="fa-solid fa-shuffle"></i>
          </button>
          <button onClick={handlePrevTrack}>
            <i className="fa-solid fa-backward-step"></i>
          </button>
          <button className="btn-play-main" onClick={togglePlay}>
            <i className={`fa-solid ${isPlaying ? 'fa-pause' : 'fa-play'}`}></i>
          </button>
          <button onClick={handleNextTrack}>
            <i className="fa-solid fa-forward-step"></i>
          </button>
          <button
            className={isRepeat ? 'active' : ''}
            onClick={() => setIsRepeat(!isRepeat)}
          >
            <i className="fa-solid fa-repeat"></i>
          </button>
        </div>
        <div className="progress-bar-container">
          <span className="time">{formatTime(currentTime)}</span>
          <div className="progress-bar" onClick={handleProgressClick}>
            <div className="progress-fill" style={{ width: `${progressPercent}%` }}></div>
          </div>
          <span className="time">{formatTime(duration)}</span>
        </div>
      </div>

      {/* 볼륨 및 기타 설정 (우측) */}
      <div className="player-extra">
        <i className={`fa-solid ${volume === 0 ? 'fa-volume-xmark' : 'fa-volume-high'}`}></i>
        <div className="volume-bar" onClick={handleVolumeClick}>
          <div className="volume-fill" style={{ width: `${volume}%` }}></div>
        </div>
      </div>
    </footer>
  );
}