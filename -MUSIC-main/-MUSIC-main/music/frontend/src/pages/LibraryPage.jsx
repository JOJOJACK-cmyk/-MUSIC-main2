import React, { useState, useEffect, useCallback } from 'react';
import Header from '../components/Header';
import MusicCard from '../components/MusicCard';
import axios from 'axios';
import api from '../api/axiosInstance';
import { usePlayer } from '../context/PlayerContext';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

const TABS = [
  { key: 'liked', label: '좋아요', icon: 'fa-heart' },
  { key: 'recent', label: '최근 들은 곡', icon: 'fa-clock-rotate-left' },
  { key: 'playlists', label: '플레이리스트', icon: 'fa-list' },
];

const DESCRIPTIONS = {
  liked: '좋아요 표시한 곡이 보관되는 공간입니다.',
  recent: '30초 이상 들은 곡이 최신순으로 쌓입니다. (최대 50곡)',
  playlists: '직접 만든 플레이리스트입니다. 곡 카드의 + 버튼으로 곡을 담을 수 있어요.',
};

function LibState({ icon, children, sub, onRetry }) {
  return (
    <div className="lib-state">
      <i className={`fa-solid ${icon}`} />
      {children}
      {sub && <div className="lib-state-sub">{sub}</div>}
      {onRetry && (
        <button className="lib-retry" onClick={onRetry}>다시 시도</button>
      )}
    </div>
  );
}

export default function LibraryPage() {
  const navigate = useNavigate();
  const { hasFeature } = useAuth() || {};
  // 만들기 · 이름 변경은 플레이리스트 기능이 있는 이용권만 (이미 만든 목록 보기 · 재생 · 정리는 누구나)
  const canEditPlaylists = Boolean(hasFeature?.('PLAYLIST'));
  const [tab, setTab] = useState('liked');
  const [likedMusics, setLikedMusics] = useState([]);
  const [recentMusics, setRecentMusics] = useState([]);
  const [playlists, setPlaylists] = useState([]);
  const [openPlaylist, setOpenPlaylist] = useState(null); // 상세 보기 중인 플레이리스트 {id,name,tracks}
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const { selectTrack } = usePlayer();

  const likedIds = new Set(likedMusics.map((m) => m.id));

  const fetchLikedMusics = useCallback(async () => {
    const response = await axios.get('/api/musics/liked', { withCredentials: true });
    const list = Array.isArray(response.data) ? response.data : [];
    setLikedMusics(list.map((m) => ({ ...m, isLiked: true })));
  }, []);

  const fetchRecent = useCallback(async () => {
    const r = await api.get('/api/musics/recent');
    setRecentMusics(Array.isArray(r.data) ? r.data : []);
  }, []);

  const fetchPlaylists = useCallback(async () => {
    const r = await api.get('/api/playlists');
    setPlaylists(Array.isArray(r.data) ? r.data : []);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      // 좋아요 목록은 다른 탭의 하트 표시에도 쓰므로 항상 불러온다
      await fetchLikedMusics();
      if (tab === 'recent') await fetchRecent();
      if (tab === 'playlists') await fetchPlaylists();
    } catch (err) {
      console.error('보관함 불러오기 실패:', err);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [tab, fetchLikedMusics, fetchRecent, fetchPlaylists]);

  useEffect(() => {
    setOpenPlaylist(null);
    load();
  }, [load]);

  // 좋아요 토글 — 좋아요 탭에서는 취소 시 목록에서 제거, 다른 탭에서는 하트만 바뀜
  const handleToggleLike = async (musicId) => {
    try {
      const res = await axios.post(`/api/musics/${musicId}/like`, {}, { withCredentials: true });
      const liked = res?.data?.liked;
      if (liked === false || liked === undefined) {
        setLikedMusics((prev) => prev.filter((music) => music.id !== musicId));
      } else {
        fetchLikedMusics().catch(() => {});
      }
      return res?.data;
    } catch (err) {
      console.error('좋아요 처리 실패:', err);
      alert('처리에 실패했습니다.');
      throw err;
    }
  };

  // ---- 플레이리스트 ----
  const createPlaylist = async () => {
    const name = window.prompt('새 플레이리스트 이름');
    if (!name || !name.trim()) return;
    try {
      await api.post('/api/playlists', { name: name.trim() });
      fetchPlaylists();
    } catch (e) {
      alert(e?.response?.data?.message || '플레이리스트를 만들지 못했습니다.');
    }
  };

  const openDetail = async (id) => {
    try {
      const r = await api.get(`/api/playlists/${id}`);
      setOpenPlaylist(r.data);
    } catch (e) {
      alert(e?.response?.data?.message || '플레이리스트를 불러오지 못했습니다.');
    }
  };

  const renamePlaylist = async () => {
    const name = window.prompt('플레이리스트 이름', openPlaylist.name);
    if (!name || !name.trim() || name.trim() === openPlaylist.name) return;
    try {
      await api.patch(`/api/playlists/${openPlaylist.id}`, { name: name.trim() });
      setOpenPlaylist((p) => ({ ...p, name: name.trim() }));
      fetchPlaylists();
    } catch (e) {
      alert(e?.response?.data?.message || '이름을 바꾸지 못했습니다.');
    }
  };

  const deletePlaylist = async () => {
    if (!window.confirm(`'${openPlaylist.name}' 플레이리스트를 삭제할까요?`)) return;
    try {
      await api.delete(`/api/playlists/${openPlaylist.id}`);
      setOpenPlaylist(null);
      fetchPlaylists();
    } catch (e) {
      alert(e?.response?.data?.message || '삭제하지 못했습니다.');
    }
  };

  const removeTrack = async (itemId) => {
    try {
      await api.delete(`/api/playlists/${openPlaylist.id}/tracks/${itemId}`);
      setOpenPlaylist((p) => ({ ...p, tracks: p.tracks.filter((t) => t.itemId !== itemId) }));
      fetchPlaylists();
    } catch (e) {
      alert(e?.response?.data?.message || '곡을 빼지 못했습니다.');
    }
  };

  const withLikes = (list) => list.map((m) => ({ ...m, isLiked: likedIds.has(m.id) }));

  const renderGrid = (list) => (
    <div className="library-grid">
      {list.map((music) => (
        <MusicCard key={music.id} music={music} queue={list} onToggleLike={handleToggleLike} />
      ))}
    </div>
  );

  const renderPlaylistDetail = () => {
    const tracks = withLikes(openPlaylist.tracks.map((t) => t.music));
    return (
      <>
        <div className="pl-detail-head">
          <button className="pl-back" onClick={() => setOpenPlaylist(null)}>
            <i className="fa-solid fa-chevron-left" /> 목록
          </button>
          <h3>{openPlaylist.name}</h3>
          <span className="library-count">{tracks.length}곡</span>
          <div className="pl-detail-actions">
            <button disabled={tracks.length === 0} onClick={() => selectTrack(tracks[0], tracks)}>
              <i className="fa-solid fa-play" /> 전체 재생
            </button>
            {canEditPlaylists && <button onClick={renamePlaylist}><i className="fa-solid fa-pen" /> 이름 변경</button>}
            <button className="danger" onClick={deletePlaylist}><i className="fa-solid fa-trash" /> 삭제</button>
          </div>
        </div>
        {tracks.length === 0 ? (
          <LibState icon="fa-music" sub="곡 카드의 + 버튼을 눌러 이 플레이리스트에 담아보세요.">
            아직 담긴 곡이 없습니다.
          </LibState>
        ) : (
          <div className="library-grid">
            {openPlaylist.tracks.map((t, i) => (
              <div key={t.itemId} className="pl-track">
                <MusicCard music={tracks[i]} queue={tracks} onToggleLike={handleToggleLike} />
                <button className="pl-remove" onClick={() => removeTrack(t.itemId)}>
                  <i className="fa-solid fa-minus" /> 빼기
                </button>
              </div>
            ))}
          </div>
        )}
      </>
    );
  };

  const renderBody = () => {
    if (loading) {
      return (
        <div className="lib-state">
          <i className="fa-solid fa-compact-disc fa-spin" />
          불러오는 중…
        </div>
      );
    }
    if (error) {
      return (
        <LibState icon="fa-triangle-exclamation" sub="로그인 상태를 확인한 뒤 다시 시도해 주세요." onRetry={load}>
          보관함을 불러오지 못했습니다.
        </LibState>
      );
    }
    if (tab === 'liked') {
      return likedMusics.length === 0 ? (
        <LibState icon="fa-heart" sub="곡 카드의 하트를 눌러 보관함에 담아보세요.">
          아직 좋아요 표시한 음악이 없습니다.
        </LibState>
      ) : renderGrid(likedMusics);
    }
    if (tab === 'recent') {
      return recentMusics.length === 0 ? (
        <LibState icon="fa-clock-rotate-left" sub="곡을 30초 이상 들으면 여기에 기록됩니다.">
          아직 들은 곡이 없습니다.
        </LibState>
      ) : renderGrid(withLikes(recentMusics));
    }
    // playlists
    if (openPlaylist) return renderPlaylistDetail();
    return (
      <div className="pl-grid">
        {canEditPlaylists ? (
          <button className="pl-card pl-card-new" onClick={createPlaylist}>
            <div className="pl-cover"><i className="fa-solid fa-plus" /></div>
            <strong>새 플레이리스트</strong>
          </button>
        ) : (
          <button className="pl-card pl-card-new" onClick={() => navigate('/payment')}>
            <div className="pl-cover"><i className="fa-solid fa-lock" /></div>
            <strong>스탠다드 이상 이용권</strong>
            <small>플레이리스트를 만들 수 있어요</small>
          </button>
        )}
        {playlists.map((p) => (
          <button key={p.id} className="pl-card" onClick={() => openDetail(p.id)}>
            <div className="pl-cover">
              {p.coverUrl ? <img src={p.coverUrl} alt="" /> : <i className="fa-solid fa-music" />}
            </div>
            <strong>{p.name}</strong>
            <small>{p.trackCount}곡</small>
          </button>
        ))}
      </div>
    );
  };

  const count =
    tab === 'liked' ? likedMusics.length : tab === 'recent' ? recentMusics.length : playlists.length;

  return (
    <>
      <Header searchTerm="" setSearchTerm={() => {}} />
      <div className="content-section">
        <div className="library-head">
          <div>
            <h2>📂 내 보관함</h2>
            <p>{DESCRIPTIONS[tab]}</p>
          </div>
          {!loading && !error && !openPlaylist && count > 0 && (
            <span className="library-count">{count}{tab === 'playlists' ? '개' : '곡'}</span>
          )}
        </div>

        <div className="lib-tabs">
          {TABS.map((t) => (
            <button
              key={t.key}
              className={`lib-tab ${tab === t.key ? 'active' : ''}`}
              onClick={() => setTab(t.key)}
            >
              <i className={`fa-solid ${t.icon}`} /> {t.label}
            </button>
          ))}
        </div>

        {renderBody()}
      </div>
    </>
  );
}
