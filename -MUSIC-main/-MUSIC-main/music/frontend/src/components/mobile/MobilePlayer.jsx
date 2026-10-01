import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axiosInstance';
import { usePlayer } from '../../context/PlayerContext';
import { useAuth } from '../../context/AuthContext';
import MobileSheet from './MobileSheet';

const fmt = (s) => {
  const n = Math.max(0, Math.floor(Number(s) || 0));
  return `${Math.floor(n / 60)}:${String(n % 60).padStart(2, '0')}`;
};
const won = (n) => `${Number(n || 0).toLocaleString('ko-KR')}원`;

/** 탭바 위 미니 플레이어. 누르면 전체화면 플레이어가 열린다. */
export function MobileMiniPlayer({ onOpen }) {
  const { currentTrack, isPlaying, togglePlay, handleNextTrack, currentTime, duration } = usePlayer();
  if (!currentTrack) return null;
  const pct = duration > 0 ? Math.min(100, (currentTime / duration) * 100) : 0;
  return (
    <div className="m-mini" onClick={onOpen} role="button" aria-label="플레이어 열기">
      <div className="m-mini-progress"><span style={{ width: `${pct}%` }} /></div>
      <div className="m-mini-thumb">
        {currentTrack.thumbnailUrl ? <img src={currentTrack.thumbnailUrl} alt="" /> : <i className="fa-solid fa-music" />}
      </div>
      <div className="m-mini-text">
        <strong>{currentTrack.title}</strong>
        <small>{currentTrack.artist || '아티스트 미상'}</small>
      </div>
      <button className="m-mini-btn" aria-label={isPlaying ? '일시정지' : '재생'} onClick={(e) => { e.stopPropagation(); togglePlay(); }}>
        <i className={`fa-solid ${isPlaying ? 'fa-pause' : 'fa-play'}`} />
      </button>
      <button className="m-mini-btn" aria-label="다음 곡" onClick={(e) => { e.stopPropagation(); handleNextTrack(); }}>
        <i className="fa-solid fa-forward-step" />
      </button>
    </div>
  );
}

/** 아래에서 올라오는 전체화면 플레이어 */
export function MobileFullPlayer({ open, onClose }) {
  const {
    currentTrack, isPlaying, togglePlay, handleNextTrack, handlePrevTrack,
    currentTime, duration, seekTime, isShuffle, setIsShuffle, isRepeat, setIsRepeat,
    playlist, playTrack,
  } = usePlayer();
  const { user } = useAuth() || {};
  const navigate = useNavigate();
  const [liked, setLiked] = useState(false);
  const [related, setRelated] = useState([]);
  const [sheet, setSheet] = useState(null); // 'related' | 'queue'
  const [dragPct, setDragPct] = useState(null);
  const startY = useRef(null);

  // 좋아요 여부 · 관련 상품 (곡이 바뀔 때마다)
  useEffect(() => {
    if (!open || !currentTrack?.id) return;
    let alive = true;
    api.get('/api/shop/products/related', { params: { musicId: currentTrack.id } })
      .then((r) => alive && setRelated(Array.isArray(r.data) ? r.data : []))
      .catch(() => alive && setRelated([]));
    if (user) {
      api.get('/api/musics/liked')
        .then((r) => alive && setLiked((Array.isArray(r.data) ? r.data : []).some((m) => String(m.id) === String(currentTrack.id))))
        .catch(() => {});
    } else {
      setLiked(false);
    }
    return () => { alive = false; };
  }, [open, currentTrack?.id, user]);

  if (!open || !currentTrack) return null;

  const pct = dragPct ?? (duration > 0 ? Math.min(100, (currentTime / duration) * 100) : 0);
  const toggleLike = async () => {
    if (!user) { onClose(); navigate('/login'); return; }
    try {
      const r = await api.post(`/api/musics/${currentTrack.id}/like`, {});
      setLiked(Boolean(r.data?.liked));
    } catch (_) {}
  };
  const idx = playlist.findIndex((t) => String(t.id) === String(currentTrack.id));
  const upNext = idx >= 0 ? playlist.slice(idx + 1) : playlist;

  // body 로 포털: .m-app(position: fixed)이 쌓임 맥락을 만들어서, 그 안에 두면 바깥의 떠 있는 부품
  // (라이브 미니 플레이어 등)이 z-index 와 상관없이 위에 그려진다.
  return createPortal(
    <div className="m-full" role="dialog" aria-label="지금 재생 중"
      onTouchStart={(e) => { startY.current = e.touches[0].clientY; }}
      onTouchEnd={(e) => { if (startY.current != null && e.changedTouches[0].clientY - startY.current > 120) onClose(); startY.current = null; }}>
      <div className="m-full-bg" style={currentTrack.thumbnailUrl ? { backgroundImage: `url(${currentTrack.thumbnailUrl})` } : undefined} />
      <div className="m-full-head">
        <button className="m-icon-btn" aria-label="닫기" onClick={onClose}><i className="fa-solid fa-chevron-down" /></button>
        <span>지금 재생 중</span>
        <button className="m-icon-btn" aria-label="재생 목록" onClick={() => setSheet('queue')}><i className="fa-solid fa-list-ul" /></button>
      </div>

      <div className="m-full-art">
        {currentTrack.thumbnailUrl ? <img src={currentTrack.thumbnailUrl} alt="" /> : <i className="fa-solid fa-music" />}
      </div>

      <div className="m-full-meta">
        <div>
          <strong>{currentTrack.title}</strong>
          <small>{currentTrack.artist || '아티스트 미상'}</small>
        </div>
        <button className={`m-like ${liked ? 'on' : ''}`} aria-label="좋아요" onClick={toggleLike}>
          <i className={`fa-${liked ? 'solid' : 'regular'} fa-heart`} />
        </button>
      </div>

      <div className="m-full-seek">
        <input
          type="range" min="0" max="100" step="0.1" value={pct}
          aria-label="재생 위치"
          style={{ '--pct': `${pct}%` }}
          onChange={(e) => setDragPct(Number(e.target.value))}
          onMouseUp={() => { if (dragPct != null) { seekTime(dragPct); setDragPct(null); } }}
          onTouchEnd={() => { if (dragPct != null) { seekTime(dragPct); setDragPct(null); } }}
        />
        <div className="m-full-times"><span>{fmt((pct / 100) * duration)}</span><span>{fmt(duration)}</span></div>
      </div>

      <div className="m-full-controls">
        <button className={isShuffle ? 'on' : ''} aria-label="셔플" onClick={() => setIsShuffle(!isShuffle)}><i className="fa-solid fa-shuffle" /></button>
        <button aria-label="이전 곡" onClick={handlePrevTrack}><i className="fa-solid fa-backward-step" /></button>
        <button className="play" aria-label={isPlaying ? '일시정지' : '재생'} onClick={togglePlay}>
          <i className={`fa-solid ${isPlaying ? 'fa-pause' : 'fa-play'}`} />
        </button>
        <button aria-label="다음 곡" onClick={handleNextTrack}><i className="fa-solid fa-forward-step" /></button>
        <button className={isRepeat ? 'on' : ''} aria-label="반복" onClick={() => setIsRepeat(!isRepeat)}><i className="fa-solid fa-repeat" /></button>
      </div>

      <div className="m-full-chips">
        {related.length > 0 && (
          <button className="m-chip accent" onClick={() => setSheet('related')}>
            <i className="fa-solid fa-bag-shopping" /> 관련 상품 {related.length}개
          </button>
        )}
        <button className="m-chip" onClick={() => setSheet('queue')}>
          <i className="fa-solid fa-list-ul" /> 다음 곡 {upNext.length}
        </button>
      </div>

      <MobileSheet open={sheet === 'related'} title="이 곡의 관련 상품" onClose={() => setSheet(null)}>
        {related.map((p) => (
          <button key={p.id} className="m-product-row" onClick={() => { setSheet(null); onClose(); navigate(`/shop/${p.id}`); }}>
            <div className="m-product-thumb">{p.imageUrl ? <img src={p.imageUrl} alt="" /> : <i className="fa-solid fa-compact-disc" />}</div>
            <span><strong>{p.name}</strong><small>{p.stock > 0 ? won(p.price) : '품절'}</small></span>
            <i className="fa-solid fa-chevron-right" />
          </button>
        ))}
      </MobileSheet>

      <MobileSheet open={sheet === 'queue'} title="다음 곡" onClose={() => setSheet(null)} tall>
        {upNext.length === 0 ? <div className="m-empty small">다음 곡이 없어요</div> : upNext.slice(0, 50).map((t) => (
          <button key={t.id} className="m-product-row" onClick={() => { playTrack(t, playlist); setSheet(null); }}>
            <div className="m-product-thumb">{t.thumbnailUrl ? <img src={t.thumbnailUrl} alt="" /> : <i className="fa-solid fa-music" />}</div>
            <span><strong>{t.title}</strong><small>{t.artist}</small></span>
            <i className="fa-solid fa-play" />
          </button>
        ))}
      </MobileSheet>
    </div>,
    document.body
  );
}
