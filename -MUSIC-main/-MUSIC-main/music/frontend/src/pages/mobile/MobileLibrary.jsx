import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axiosInstance';
import { usePlayer } from '../../context/PlayerContext';
import TrackRow, { toTrack } from '../../components/mobile/TrackRow';

const TABS = [
  { key: 'liked', label: '좋아요' },
  { key: 'recent', label: '최근 들은 곡' },
  { key: 'playlists', label: '플레이리스트' },
];

/** 모바일 보관함: 좋아요 · 최근 들은 곡 · 플레이리스트(상세 포함) — 모두 한 줄 목록 */
export default function MobileLibrary() {
  const navigate = useNavigate();
  const { selectTrack } = usePlayer();
  const [tab, setTab] = useState('liked');
  const [tracks, setTracks] = useState(null);
  const [playlists, setPlaylists] = useState(null);
  const [detail, setDetail] = useState(null); // { id, name, tracks: [{itemId, music}] }

  // 탭을 빠르게 바꿨을 때 이전 탭의 늦은 응답이 지금 탭 목록을 덮지 않도록 요청 번호로 거른다
  const reqSeq = useRef(0);
  const load = useCallback(async () => {
    const seq = ++reqSeq.current;
    const stale = () => seq !== reqSeq.current;
    setDetail(null);
    try {
      if (tab === 'playlists') {
        setPlaylists(null);
        const r = await api.get('/api/playlists');
        if (!stale()) setPlaylists(Array.isArray(r.data) ? r.data : []);
      } else {
        setTracks(null);
        const r = await api.get(tab === 'liked' ? '/api/musics/liked' : '/api/musics/recent');
        if (!stale()) setTracks((Array.isArray(r.data) ? r.data : []).map(toTrack));
      }
    } catch (_) {
      if (stale()) return;
      if (tab === 'playlists') setPlaylists([]); else setTracks([]);
    }
  }, [tab]);

  useEffect(() => { load(); }, [load]);

  const openDetail = async (id) => {
    try { setDetail((await api.get(`/api/playlists/${id}`)).data); } catch (_) {}
  };
  const createPlaylist = async () => {
    const name = window.prompt('새 플레이리스트 이름');
    if (!name || !name.trim()) return;
    try { await api.post('/api/playlists', { name: name.trim() }); load(); } catch (e) { alert(e?.response?.data?.message || '만들지 못했어요'); }
  };
  const renamePlaylist = async () => {
    const name = window.prompt('플레이리스트 이름', detail.name);
    if (!name || !name.trim() || name.trim() === detail.name) return;
    try { await api.patch(`/api/playlists/${detail.id}`, { name: name.trim() }); setDetail((d) => ({ ...d, name: name.trim() })); } catch (_) {}
  };
  const deletePlaylist = async () => {
    if (!window.confirm(`'${detail.name}' 플레이리스트를 삭제할까요?`)) return;
    try { await api.delete(`/api/playlists/${detail.id}`); load(); } catch (_) {}
  };
  const removeTrack = async (itemId) => {
    try {
      await api.delete(`/api/playlists/${detail.id}/tracks/${itemId}`);
      setDetail((d) => ({ ...d, tracks: d.tracks.filter((t) => t.itemId !== itemId) }));
    } catch (_) {}
  };

  // 플레이리스트 상세
  if (detail) {
    const list = detail.tracks.map((t) => toTrack(t.music));
    return (
      <div className="m-page">
        <button className="m-text-btn back" onClick={() => setDetail(null)}><i className="fa-solid fa-chevron-left" /> 플레이리스트</button>
        <div className="m-pl-hero">
          <div className="m-pl-cover">{list[0]?.thumbnailUrl ? <img src={list[0].thumbnailUrl} alt="" /> : <i className="fa-solid fa-music" />}</div>
          <div>
            <h2>{detail.name}</h2>
            <p>{list.length}곡</p>
            <div className="m-pl-actions">
              <button className="m-btn primary small" disabled={!list.length} onClick={() => list.length && selectTrack(list[0], list)}>
                <i className="fa-solid fa-play" /> 전체 재생
              </button>
              <button className="m-icon-btn" aria-label="이름 변경" onClick={renamePlaylist}><i className="fa-solid fa-pen" /></button>
              <button className="m-icon-btn danger" aria-label="삭제" onClick={deletePlaylist}><i className="fa-solid fa-trash" /></button>
            </div>
          </div>
        </div>
        {list.length === 0 ? (
          <div className="m-empty small">곡 ⋯ 메뉴에서 ‘플레이리스트에 담기’로 곡을 담아 보세요</div>
        ) : (
          <div className="m-list">
            {detail.tracks.map((t, i) => (
              <TrackRow key={t.itemId} track={list[i]} queue={list} onRemove={() => removeTrack(t.itemId)} />
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="m-page">
      <div className="m-segment">
        {TABS.map((t) => (
          <button key={t.key} className={tab === t.key ? 'active' : ''} onClick={() => setTab(t.key)}>{t.label}</button>
        ))}
      </div>

      {tab === 'playlists' ? (
        playlists === null ? <div className="m-empty"><i className="fa-solid fa-compact-disc fa-spin" />불러오는 중…</div> : (
          <div className="m-list">
            <button className="m-pl-row new" onClick={createPlaylist}>
              <div className="m-pl-thumb"><i className="fa-solid fa-plus" /></div>
              <span><strong>새 플레이리스트</strong></span>
            </button>
            {playlists.map((p) => (
              <button key={p.id} className="m-pl-row" onClick={() => openDetail(p.id)}>
                <div className="m-pl-thumb">{p.coverUrl ? <img src={p.coverUrl} alt="" /> : <i className="fa-solid fa-music" />}</div>
                <span><strong>{p.name}</strong><small>{p.trackCount}곡</small></span>
                <i className="fa-solid fa-chevron-right" />
              </button>
            ))}
          </div>
        )
      ) : tracks === null ? (
        <div className="m-empty"><i className="fa-solid fa-compact-disc fa-spin" />불러오는 중…</div>
      ) : tracks.length === 0 ? (
        <div className="m-empty">
          <i className={`fa-solid ${tab === 'liked' ? 'fa-heart' : 'fa-clock-rotate-left'}`} />
          {tab === 'liked' ? '좋아요한 곡이 없어요' : '30초 이상 들은 곡이 여기에 쌓여요'}
          <button className="m-btn ghost small" onClick={() => navigate('/charts')}>차트에서 찾아보기</button>
        </div>
      ) : (
        <>
          <button className="m-btn primary small play-all" onClick={() => selectTrack(tracks[0], tracks)}>
            <i className="fa-solid fa-play" /> 전체 재생 · {tracks.length}곡
          </button>
          <div className="m-list">{tracks.map((t) => <TrackRow key={t.id} track={t} queue={tracks} />)}</div>
        </>
      )}
    </div>
  );
}
