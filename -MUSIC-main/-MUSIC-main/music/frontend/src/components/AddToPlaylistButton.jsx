import React, { useEffect, useRef, useState } from 'react';
import api from '../api/axiosInstance';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * 곡 카드의 "플레이리스트에 담기" 버튼. 누르면 내 플레이리스트 목록이 뜨고,
 * 고르면 그 플레이리스트에 담는다. 목록 맨 아래에서 새 플레이리스트를 바로 만들 수도 있다.
 */
export default function AddToPlaylistButton({ musicId }) {
  const { user, hasFeature } = useAuth() || {};
  const navigate = useNavigate();
  // 플레이리스트 만들기 · 담기는 스탠다드 이상 이용권 (보기 · 재생은 누구나)
  const locked = !hasFeature?.('PLAYLIST');
  const [open, setOpen] = useState(false);
  const [playlists, setPlaylists] = useState(null); // null = 불러오는 중
  const [message, setMessage] = useState('');
  const wrapRef = useRef(null);

  // 바깥 클릭 시 닫기
  useEffect(() => {
    if (!open) return;
    const onDown = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  if (!user) return null;

  const flash = (text) => {
    setMessage(text);
    setTimeout(() => setMessage(''), 2000);
  };

  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (!next || locked) return;
    setPlaylists(null);
    try {
      const r = await api.get('/api/playlists');
      setPlaylists(Array.isArray(r.data) ? r.data : []);
    } catch (_) {
      setPlaylists([]);
    }
  };

  const addTo = async (playlistId) => {
    try {
      await api.post(`/api/playlists/${playlistId}/tracks`, { musicId });
      flash('담았어요');
      setOpen(false);
    } catch (e) {
      flash(e?.response?.status === 409 ? '이미 담긴 곡이에요' : (e?.response?.data?.message || '담지 못했어요'));
    }
  };

  const createAndAdd = async () => {
    const name = window.prompt('새 플레이리스트 이름');
    if (!name || !name.trim()) return;
    try {
      const r = await api.post('/api/playlists', { name: name.trim() });
      await addTo(r.data.id);
    } catch (e) {
      flash(e?.response?.data?.message || '만들지 못했어요');
    }
  };

  return (
    <div className="pl-add" ref={wrapRef}>
      <button onClick={toggle} title="플레이리스트에 담기">
        <i className="fa-solid fa-plus" />
      </button>
      {message && <span className="pl-add-flash">{message}</span>}
      {open && (
        <div className="pl-add-menu">
          <div className="pl-add-title">플레이리스트에 담기</div>
          {locked ? (
            <>
              <div className="pl-add-empty">플레이리스트는 스탠다드 · 프리미엄 이용권에서 쓸 수 있어요</div>
              <button className="pl-add-item pl-add-new" onClick={() => { setOpen(false); navigate('/payment'); }}>
                <i className="fa-solid fa-ticket" /> 이용권 보기
              </button>
            </>
          ) : playlists === null ? (
            <div className="pl-add-empty">불러오는 중…</div>
          ) : playlists.length === 0 ? (
            <div className="pl-add-empty">아직 플레이리스트가 없어요</div>
          ) : (
            playlists.map((p) => (
              <button key={p.id} className="pl-add-item" onClick={() => addTo(p.id)}>
                <span>{p.name}</span>
                <small>{p.trackCount}곡</small>
              </button>
            ))
          )}
          {!locked && (
            <button className="pl-add-item pl-add-new" onClick={createAndAdd}>
              <i className="fa-solid fa-plus" /> 새 플레이리스트
            </button>
          )}
        </div>
      )}
    </div>
  );
}
