import React, { useState, useEffect } from 'react';
import { usePlayer } from '../context/PlayerContext';

export default function MusicCard({
  music,
  onEdit,
  onDelete,
  onToggleLike,
  isAdmin // 💡 MainPage에서 전달받은 관리자 여부 props
}) {
  const {
    playTrack,
    togglePlay,
    currentTrack,
    isPlaying
  } = usePlayer();

  // 좋아요 상태 관리
  const [isLiked, setIsLiked] = useState(music.isLiked || false);

  // 💡 부모 컴포넌트에서 내려주는 music.isLiked 값이 변경될 때마다(로그아웃 또는 동기화 시) 상태 즉시 업데이트
  useEffect(() => {
    setIsLiked(music.isLiked || false);
  }, [music.isLiked]);

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
    setIsLiked(nextLiked); // 낙관적 업데이트(UI 먼저 반영)

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

      {/* 하단 액션 영역 (좋아요 + 관리자 전용 수정/삭제) */}
      <div
        className="card-actions"
        onClick={(e) => e.stopPropagation()}
      >
        {/* ❤️ 좋아요 버튼은 누구나 볼 수 있음 */}
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

        {/* 💡 오직 관리자(isAdmin이 true)일 때만 수정/삭제 버튼이 렌더링됨 */}
        {isAdmin && (
          <>
            {onEdit && (
              <button onClick={() => onEdit(music)} title="수정">
                <i className="fa-solid fa-pen-to-square"></i>
              </button>
            )}

            {onDelete && (
              <button onClick={() => onDelete(music.id)} title="삭제">
                <i className="fa-solid fa-trash"></i>
              </button>
            )}
          </>
        )}
      </div>
    </div>
  );
}