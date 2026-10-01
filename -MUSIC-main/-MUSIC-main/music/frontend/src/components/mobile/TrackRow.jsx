import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axiosInstance';
import { usePlayer } from '../../context/PlayerContext';
import { useAuth } from '../../context/AuthContext';
import MobileSheet from './MobileSheet';

/** API 응답(곡/차트 항목)을 플레이어가 쓰는 곡 형태로 */
export const toTrack = (m) => ({
  id: m.id ?? m.musicId,
  youtubeVideoId: m.youtubeVideoId,
  title: m.title,
  artist: m.artist,
  thumbnailUrl: m.thumbnailUrl,
});

/**
 * 모바일 곡 한 줄: [순위] 썸네일 · 제목/아티스트 · ⋯
 * 줄을 누르면 queue 안에서 재생, ⋯ 는 좋아요 · 플레이리스트에 담기 시트.
 */
export default function TrackRow({ track, queue, rank, right, onRemove }) {
  const { selectTrack, currentTrack, isPlaying } = usePlayer();
  const isCurrent = currentTrack && String(currentTrack.id) === String(track.id);
  const [menu, setMenu] = useState(false);

  return (
    <>
      <div className={`m-row ${isCurrent ? 'current' : ''}`} onClick={() => selectTrack(track, queue)}>
        {rank != null && <span className={`m-rank ${rank <= 3 ? 'top' : ''}`}>{rank}</span>}
        <div className="m-row-thumb">
          {track.thumbnailUrl ? <img src={track.thumbnailUrl} alt="" loading="lazy" /> : <i className="fa-solid fa-music" />}
          {isCurrent && isPlaying && <span className="m-eq"><i /><i /><i /></span>}
        </div>
        <div className="m-row-text">
          <strong>{track.title}</strong>
          <small>{track.artist || '아티스트 미상'}</small>
        </div>
        {right}
        <button className="m-row-more" aria-label="더보기" onClick={(e) => { e.stopPropagation(); setMenu(true); }}>
          <i className="fa-solid fa-ellipsis-vertical" />
        </button>
      </div>
      {menu && <TrackActions track={track} onClose={() => setMenu(false)} onRemove={onRemove} />}
    </>
  );
}

/** 곡 ⋯ 시트: 좋아요 · 플레이리스트에 담기 (· 빼기) */
export function TrackActions({ track, onClose, onRemove }) {
  const { user, hasFeature } = useAuth() || {};
  const navigate = useNavigate();
  const canPlaylist = Boolean(hasFeature?.('PLAYLIST'));
  const [playlists, setPlaylists] = useState(null);
  const [msg, setMsg] = useState('');

  const needLogin = () => { onClose(); navigate('/login'); };
  const flash = (t) => { setMsg(t); setTimeout(() => setMsg(''), 1800); };

  const like = async () => {
    if (!user) return needLogin();
    try {
      const r = await api.post(`/api/musics/${track.id}/like`, {});
      flash(r.data?.liked ? '좋아요에 담았어요' : '좋아요를 취소했어요');
    } catch (_) { flash('처리하지 못했어요'); }
  };
  const loadPlaylists = async () => {
    if (!user) return needLogin();
    if (!canPlaylist) { onClose(); navigate('/payment'); return; }
    try {
      const r = await api.get('/api/playlists');
      setPlaylists(Array.isArray(r.data) ? r.data : []);
    } catch (_) { setPlaylists([]); }
  };
  const addTo = async (id) => {
    try {
      await api.post(`/api/playlists/${id}/tracks`, { musicId: track.id });
      flash('플레이리스트에 담았어요');
      setPlaylists(null);
    } catch (e) {
      flash(e?.response?.status === 409 ? '이미 담긴 곡이에요' : '담지 못했어요');
    }
  };
  const createAndAdd = async () => {
    const name = window.prompt('새 플레이리스트 이름');
    if (!name || !name.trim()) return;
    try {
      const r = await api.post('/api/playlists', { name: name.trim() });
      await addTo(r.data.id);
    } catch (e) { flash(e?.response?.data?.message || '만들지 못했어요'); }
  };

  return (
    <MobileSheet open title={track.title} onClose={onClose}>
      {msg && <div className="m-toast-inline">{msg}</div>}
      {playlists === null ? (
        <div className="m-actions">
          <button onClick={like}><i className="fa-solid fa-heart" /> 좋아요</button>
          <button onClick={loadPlaylists}>
            <i className={`fa-solid ${user && !canPlaylist ? 'fa-lock' : 'fa-list'}`} /> 플레이리스트에 담기
            {user && !canPlaylist && <small>스탠다드 이상</small>}
          </button>
          {onRemove && (
            <button className="danger" onClick={() => { onRemove(); onClose(); }}>
              <i className="fa-solid fa-minus" /> 이 플레이리스트에서 빼기
            </button>
          )}
        </div>
      ) : (
        <div className="m-actions">
          {playlists.map((p) => (
            <button key={p.id} onClick={() => addTo(p.id)}>
              <i className="fa-solid fa-music" /> {p.name} <small>{p.trackCount}곡</small>
            </button>
          ))}
          <button className="accent" onClick={createAndAdd}><i className="fa-solid fa-plus" /> 새 플레이리스트</button>
        </div>
      )}
    </MobileSheet>
  );
}
