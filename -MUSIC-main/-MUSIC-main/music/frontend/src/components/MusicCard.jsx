import React, { useState } from 'react';
import { usePlayer } from '../context/PlayerContext';

export default function MusicCard({
  music,
  onEdit,
  onDelete,
  onToggleLike
}) {
  const {
    playTrack,
    togglePlay,
    currentTrack,
    isPlaying
  } = usePlayer();

  // 좋아요 상태 관리 (초기값은 music 객체에 있는 값 또는 false)
  const [isLiked, setIsLiked] = useState(music.isLiked || false);

  const isCurrent = currentTrack?.id === music.id;

  const handlePlay = () => {
    if (isCurrent) {
      togglePlay();
      return;
    }
    playTrack(music);
  };

  // ❤️ 좋아요 버튼 클릭 핸들러
  const handleLikeClick = (e) => {
    e.stopPropagation(); // 카드 전체 재생 이벤트 방지
    const nextLiked = !isLiked;
    setIsLiked(nextLiked);

    if (onToggleLike) {
      onToggleLike(music.id, nextLiked);
    }
  };

  return (
    <div
      className="music-card"
      onClick={handlePlay}
    >
      <div className="card-img">
        {music.thumbnailUrl ? (
          <img
            src={music.thumbnailUrl}
            alt={music.title}
          />
        ) : (
          <i className="fa-solid fa-music"></i>
        )}

        {/* 카드 가운데 재생 버튼 */}
        <button
          className="play-btn-hover"
          onClick={(e) => {
            e.stopPropagation();
            handlePlay();
          }}
        >
          <i
            className={
              `fa-solid ${
                isCurrent && isPlaying
                  ? 'fa-pause'
                  : 'fa-play'
              }`
            }
          ></i>
        </button>
      </div>

      {/* 곡 제목 */}
      <h4>
        {music.title}
      </h4>

      {/* 아티스트 */}
      <p>
        {music.artist || '아티스트 미상'}
      </p>

      {/* 하단 액션 영역 (좋아요 + 수정 + 삭제) */}
      <div
        className="card-actions"
        onClick={(e) => e.stopPropagation()}
      >
        {/* ❤️ 좋아요 버튼 (수정/삭제 버튼 옆) */}
        <button
          onClick={handleLikeClick}
          style={{ background: 'none', border: 'none', cursor: 'pointer' }}
          title="좋아요"
        >
          <i
            className={`fa-${isLiked ? 'solid' : 'regular'} fa-heart`}
            style={{ color: isLiked ? '#ff4757' : 'inherit' }}
          ></i>
        </button>

        {onEdit && (
          <button onClick={() => onEdit(music)}>
            <i className="fa-solid fa-pen-to-square"></i>
          </button>
        )}

        {onDelete && (
          <button onClick={() => onDelete(music.id)}>
            <i className="fa-solid fa-trash"></i>
          </button>
        )}
      </div>
    </div>
  );
}