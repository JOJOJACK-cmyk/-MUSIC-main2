import React, { useEffect, useState } from 'react';
import Header from '../components/Header';
import HeroBanner from '../components/HeroBanner';
import MusicCard from '../components/MusicCard';
import MusicModal from '../components/MusicModal';
import { musicApi } from '../api/musicApi';
import { usePlayer } from '../context/PlayerContext';

export default function MainPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [musics, setMusics] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [editingMusic, setEditingMusic] = useState(null);
  const [loading, setLoading] = useState(true);
  const [apiError, setApiError] = useState('');
  const [isAdmin, setIsAdmin] = useState(false); // 💡 관리자 여부를 상태로 관리
  const { setPlaylist, currentTrack } = usePlayer();

  // 💡 마운트될 때 및 로컬스토리지 변경 시 관리자 권한 엄격 체크
  useEffect(() => {
    try {
      const rawUser = localStorage.getItem('user');
      if (rawUser) {
        const userObj = JSON.parse(rawUser);
        // role이 'ROLE_ADMIN'인지 정확히 확인
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
      const res = await musicApi.getAllMusics();
      const data = Array.isArray(res.data) ? res.data : [];
      setMusics(data);
      setPlaylist(data);
    } catch (err) {
      console.error('음원 목록 조회 실패:', err);
      setMusics([]);
      setPlaylist([]);
      setApiError('Spring Boot 서버 또는 /api/musics 연결을 확인해 주세요.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMusics();
  }, []);

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

      {/* 💡 중복되던 메인 화면의 '+ 음원 등록' 버튼 영역을 제거하여 사이드바 버튼만 남김 */}

      <section className="content-section">
        <div className="section-header">
          <h2>DB 음악 목록</h2>
          <span style={{ color: 'var(--text-sub)', fontSize: '14px' }}>
            {musics.length}곡
          </span>
        </div>

        {loading && <p style={{ color: 'var(--text-sub)' }}>음악 목록을 불러오는 중입니다...</p>}

        {!loading && apiError && (
          <p style={{ color: '#ff6b6b', lineHeight: 1.6 }}>{apiError}</p>
        )}

        {!loading && !apiError && filteredMusics.length === 0 && (
          <p style={{ color: 'var(--text-sub)' }}>
            {searchTerm ? '검색 결과가 없습니다.' : 'DB에 등록된 음악이 없습니다.'}
          </p>
        )}

        <div className="card-grid">
          {filteredMusics.map((music) => (
            <MusicCard
              key={music.id}
              music={music}
              isAdmin={isAdmin} // 💡 MusicCard로 관리자 여부 전달
              onEdit={(item) => {
                setEditingMusic(item);
                setIsModalOpen(true);
              }}
              onDelete={handleDelete}
            />
          ))}
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