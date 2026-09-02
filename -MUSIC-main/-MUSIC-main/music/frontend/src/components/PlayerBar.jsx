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
    e.currentTarget.blur();
    const rect = e.currentTarget.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const width = rect.width;
    seekTime((clickX / width) * 100);
  };

  const handleVolumeClick = (e) => {
    e.currentTarget.blur();
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
          {/* 셔플 버튼 */}
          <button
            type="button"
            tabIndex={-1}
            onMouseDown={(e) => e.currentTarget.blur()}
            className={isShuffle ? 'active' : ''}
            onClick={(e) => {
              e.currentTarget.blur();
              setIsShuffle(!isShuffle);
            }}
          >
            <i className="fa-solid fa-shuffle"></i>
          </button>

          {/* 이전 곡 버튼 */}
          <button
            type="button"
            tabIndex={-1}
            onMouseDown={(e) => e.currentTarget.blur()}
            onClick={(e) => {
              e.currentTarget.blur();
              handlePrevTrack();
            }}
          >
            <i className="fa-solid fa-backward-step"></i>
          </button>

          {/* 메인 재생/일시정지 버튼 */}
          <button
            type="button"
            tabIndex={-1}
            onMouseDown={(e) => e.currentTarget.blur()}
            className="btn-play-main"
            onClick={(e) => {
              e.currentTarget.blur();
              togglePlay();
            }}
          >
            <i className={`fa-solid ${isPlaying ? 'fa-pause' : 'fa-play'}`}></i>
          </button>

          {/* 다음 곡 버튼 */}
          <button
            type="button"
            tabIndex={-1}
            onMouseDown={(e) => e.currentTarget.blur()}
            onClick={(e) => {
              e.currentTarget.blur();
              handleNextTrack();
            }}
          >
            <i className="fa-solid fa-forward-step"></i>
          </button>

          {/* 반복 재생 버튼 */}
          <button
            type="button"
            tabIndex={-1}
            onMouseDown={(e) => e.currentTarget.blur()}
            className={isRepeat ? 'active' : ''}
            onClick={(e) => {
              e.currentTarget.blur();
              setIsRepeat(!isRepeat);
            }}
          >
            <i className="fa-solid fa-repeat"></i>
          </button>
        </div>

        <div className="progress-bar-container">
          <span className="time">{formatTime(currentTime)}</span>
          <div
            className="progress-bar"
            tabIndex={-1}
            onMouseDown={(e) => e.currentTarget.blur()}
            onClick={handleProgressClick}
            style={{ outline: 'none', userSelect: 'none' }}
          >
            <div className="progress-fill" style={{ width: `${progressPercent}%` }}></div>
          </div>
          <span className="time">{formatTime(duration)}</span>
        </div>
      </div>

      {/* 볼륨 및 기타 설정 (우측) */}
      <div className="player-extra">
        {/* 음소거/볼륨 아이콘 버튼 */}
        <button
          type="button"
          tabIndex={-1}
          onMouseDown={(e) => e.currentTarget.blur()}
          onClick={(e) => {
            e.currentTarget.blur();
            setVolume(volume === 0 ? 50 : 0);
          }}
          style={{
            background: 'transparent',
            border: 'none',
            color: 'var(--text-sub)',
            cursor: 'pointer',
            fontSize: '16px',
            padding: '4px 6px',
            outline: 'none',
            boxShadow: 'none',
          }}
        >
          <i
            className={`fa-solid ${volume === 0 ? 'fa-volume-xmark' : 'fa-volume-high'}`}
            style={{ pointerEvents: 'none' }}
          ></i>
        </button>

        {/* 볼륨 조절 바 (사사이익 클릭 시 생기는 시작점 잔상 오차 방지용 스타일 인라인 보완) */}
        <div
          className="volume-bar"
          tabIndex={-1}
          onMouseDown={(e) => e.currentTarget.blur()}
          onClick={handleVolumeClick}
          style={{
            outline: 'none',
            userSelect: 'none',
            overflow: 'hidden',
            position: 'relative',
          }}
        >
          <div className="volume-fill" style={{ width: `${volume}%` }}></div>
        </div>
      </div>
    </footer>
  );
}