import React, { useState, useEffect } from 'react';
import { usePlayer } from '../context/PlayerContext';
import AddToPlaylistButton from './AddToPlaylistButton';

export default function MusicCard({
  music,
  onEdit,
  onDelete,
  onToggleLike,
  isAdmin, // 💡 MainPage에서 전달받은 관리자 여부 props
  queue // 💡 이 카드가 속한 목록 — 재생 시 이 목록 안에서만 다음곡이 이어짐
}) {
  const {
    selectTrack,
    currentTrack,
    isPlaying
  } = usePlayer();

  // 좋아요 상태 관리
  const [isLiked, setIsLiked] = useState(music.isLiked || false);

  // 💡 부모 컴포넌트에서 내려주는 music.isLiked 값이 변경될 때마다 상태 즉시 업데이트
  useEffect(() => {
    setIsLiked(music.isLiked || false);
  }, [music.isLiked]);

  const isCurrent = currentTrack?.id === music.id;

  const handlePlay = () => {
    selectTrack(music, Array.isArray(queue) ? queue : undefined);
  };

  // ❤️ 좋아요 버튼 클릭 핸들러
  const handleLikeClick = async (e) => {
    e.stopPropagation(); // 카드 전체 재생 이벤트 방지
    const prevLiked = isLiked;
    const nextLiked = !isLiked;
    setIsLiked(nextLiked); // 낙관적 업데이트(UI 먼저 반영)

    if (!onToggleLike) return;
    try {
      const result = await onToggleLike(music.id, nextLiked);
      // 서버가 실제 좋아요 상태를 돌려주면 그 값으로 확정
      if (result && typeof result.liked === 'boolean') {
        setIsLiked(result.liked);
      }
    } catch (_) {
      setIsLiked(prevLiked); // 실패 시 롤백
    }
  };

  return (
    <div
      className={`music-card ${isCurrent && isPlaying ? 'is-playing' : ''}`}
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

        {/* 재생 중 이퀄라이저 배지 */}
        {isCurrent && isPlaying && (
          <div className="card-eq" aria-hidden="true">
            <span /><span /><span />
          </div>
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
        {/* ❤️ 좋아요 버튼 (깜빡임 완벽 차단 및 부드러운 전환 효과 적용) */}
        <button
          onClick={handleLikeClick}
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '24px',
            height: '24px',
            outline: 'none',
          }}
          title="좋아요"
        >
          <i
            className="fa-solid fa-heart"
            style={{
              color: isLiked ? '#ff4757' : 'var(--text-sub)',
              transform: isLiked ? 'scale(1.15)' : 'scale(1)',
              transition: 'color 0.2s ease, transform 0.2s ease',
              fontSize: '15px',
            }}
          ></i>
        </button>

        {/* ➕ 플레이리스트에 담기 (로그인 사용자만 보임) */}
        <AddToPlaylistButton musicId={music.id} />

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