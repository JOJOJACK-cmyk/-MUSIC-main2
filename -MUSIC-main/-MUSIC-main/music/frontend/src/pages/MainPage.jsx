import React, { useEffect, useState, useRef } from 'react';
import Header from '../components/Header';
import HeroBanner from '../components/HeroBanner';
import MusicCard from '../components/MusicCard';
import MusicModal from '../components/MusicModal';
import { musicApi } from '../api/musicApi';
import { usePlayer } from '../context/PlayerContext';
import axios from 'axios';

export default function MainPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [musics, setMusics] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [editingMusic, setEditingMusic] = useState(null);
  const [loading, setLoading] = useState(true);
  const [apiError, setApiError] = useState('');
  const [isAdmin, setIsAdmin] = useState(false);
  const { setPlaylist, currentTrack } = usePlayer();

  // 💡 가로 스크롤 제어를 위한 useRef
  const scrollRef = useRef(null);

  // 💡 좌우 화살표 클릭 시 스크롤 이동 함수
  const scroll = (direction) => {
    if (scrollRef.current) {
      const { scrollLeft, clientWidth } = scrollRef.current;
      const scrollAmount = clientWidth * 0.75;

      scrollRef.current.scrollTo({
        left: direction === 'left' ? scrollLeft - scrollAmount : scrollLeft + scrollAmount,
        behavior: 'smooth'
      });
    }
  };

  // 💡 관리자 권한 및 로그인 여부 확인
  useEffect(() => {
    try {
      const rawUser = localStorage.getItem('user');
      if (rawUser) {
        const userObj = JSON.parse(rawUser);
        if (userObj && userObj.role === 'ROLE_ADMIN') {
          setIsAdmin(true);
        } else {
          setIsAdmin(false);
        }
      } else {
        setIsAdmin(false);
      }
    } catch (e) {
      console.error('사용자 권한 확인 중 오류:', e);
      setIsAdmin(false);
    }
  }, []);

  const fetchMusics = async () => {
    setLoading(true);
    setApiError('');

    try {
      // 💡 1. 실시간 차트 목록 불러오기
      const chartRes = await axios.get('http://localhost:8080/api/chart/realtime').catch(() => ({ data: [] }));
      const chartItems = Array.isArray(chartRes.data) ? chartRes.data : [];

      const mappedMusics = chartItems.map((item) => ({
        id: item.musicId || item.id,
        title: item.title,
        artist: item.artist,
        thumbnailUrl: item.thumbnailUrl,
        youtubeVideoId: item.youtubeVideoId,
        playCount: item.playCount
      }));

      // 💡 2. 로컬스토리지에 'user' 정보가 실제로 존재할 때만 좋아요 목록을 가져옴 (로그아웃 상태면 아예 스킵)
      let likedIds = new Set();
      const rawUser = localStorage.getItem('user');

      if (rawUser) {
        try {
          const likedRes = await axios.get('http://localhost:8080/api/musics/liked', { withCredentials: true });
          const likedMusics = Array.isArray(likedRes.data) ? likedRes.data : [];
          likedIds = new Set(likedMusics.map((m) => m.id));
        } catch (e) {
          // 인증 만료 등의 이유로 실패 시 빈 세트 유지
        }
      }

      // 💡 3. 좋아요 여부 매핑 (비로그인 상태면 likedIds가 비어있으므로 모두 false가 됨)
      const musicsWithLike = mappedMusics.map((music) => ({
        ...music,
        isLiked: likedIds.has(music.id),
      }));

      setMusics(musicsWithLike);
      setPlaylist(musicsWithLike);
    } catch (err) {
      console.error('추천 노래 차트 조회 실패:', err);
      setMusics([]);
      setPlaylist([]);
      setApiError('Spring Boot 서버 또는 /api/chart/realtime 연결을 확인해 주세요.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMusics();
  }, []);

  // 💡 ❤️ 하트(좋아요) 토글 핸들러
  const handleToggleLike = async (musicId, nextLiked) => {
    try {
      await axios.post(`http://localhost:8080/api/musics/${musicId}/like`, {}, {
        withCredentials: true,
      });

      setMusics((prevMusics) =>
        prevMusics.map((m) => (m.id === musicId ? { ...m, isLiked: nextLiked } : m))
      );
    } catch (err) {
      console.error('좋아요 토글 실패:', err);
      if (err.response?.status === 401 || err.response?.status === 403) {
        alert('로그인이 필요한 서비스입니다.');
      } else {
        alert('좋아요 처리에 실패했습니다.');
      }
    }
  };

  const handleCreateOrUpdate = async (payload) => {
    try {
      if (editingMusic) {
        await musicApi.updateMusic(editingMusic.id, payload);
        alert('음원이 수정되었습니다.');
      } else {
        await musicApi.createMusicFromYouTube(payload.youtubeVideoId);
        alert('YouTube 음원이 DB에 등록되었습니다.');
      }

      setIsModalOpen(false);
      setEditingMusic(null);
      await fetchMusics();
    } catch (err) {
      console.error(err);

      if (err.response?.status === 401 || err.response?.status === 403) {
        alert('관리자 권한이 없거나 로그인이 필요합니다.');
        return;
      }

      alert('음원 등록에 실패했습니다. 입력하신 정보나 유튜브 링크를 다시 확인해 주세요.');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('정말 삭제하시겠습니까?')) return;

    try {
      await musicApi.deleteMusic(id);
      await fetchMusics();
      alert('삭제되었습니다.');
    } catch (err) {
      if (err.response?.status === 401 || err.response?.status === 403) {
        alert('권한이 없습니다.');
        return;
      }
      alert('삭제 중 오류가 발생했습니다.');
    }
  };

  const filteredMusics = musics.filter((m) => {
    const title = m.title || '';
    const artist = m.artist || '';
    const keyword = searchTerm.toLowerCase();

    return title.toLowerCase().includes(keyword) || artist.toLowerCase().includes(keyword);
  });

  return (
    <main className="main-content">
      <Header searchTerm={searchTerm} setSearchTerm={setSearchTerm} />
      <HeroBanner />

      <section className="content-section">
        <div className="section-header">
          <h2>✨ 실시간 인기 급상승 곡</h2>
          <span style={{ color: 'var(--text-sub)', fontSize: '14px' }}>
            {musics.length}곡
          </span>
        </div>

        {loading && <p style={{ color: 'var(--text-sub)' }}>인기 급상승 곡 목록을 불러오는 중입니다...</p>}

        {!loading && apiError && (
          <p style={{ color: '#ff6b6b', lineHeight: 1.6 }}>{apiError}</p>
        )}

        {!loading && !apiError && filteredMusics.length === 0 && (
          <p style={{ color: 'var(--text-sub)' }}>
            {searchTerm ? '검색 결과가 없습니다.' : '집계된 인기 곡이 없습니다.'}
          </p>
        )}

        <div className="scroll-container-wrapper">
          <button className="scroll-btn left" onClick={() => scroll('left')} title="왼쪽으로 이동">
            <i className="fa-solid fa-chevron-left"></i>
          </button>

          <div className="card-grid-horizontal" ref={scrollRef}>
            {filteredMusics.map((music) => (
              <MusicCard
                key={music.id}
                music={music}
                isAdmin={isAdmin}
                onToggleLike={handleToggleLike}
                onEdit={(item) => {
                  setEditingMusic(item);
                  setIsModalOpen(true);
                }}
                onDelete={handleDelete}
              />
            ))}
          </div>

          <button className="scroll-btn right" onClick={() => scroll('right')} title="오른쪽으로 이동">
            <i className="fa-solid fa-chevron-right"></i>
          </button>
        </div>
      </section>

      <MusicModal
        isOpen={isModalOpen}
        onClose={() => {
          setIsModalOpen(false);
          setEditingMusic(null);
        }}
        onSubmit={handleCreateOrUpdate}
        initialData={editingMusic}
      />
    </main>
  );
}