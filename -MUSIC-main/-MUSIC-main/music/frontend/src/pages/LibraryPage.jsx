import React, { useState, useEffect } from 'react';
import Header from '../components/Header';
import MusicCard from '../components/MusicCard';
import axios from 'axios';

export default function LibraryPage() {
  const [likedMusics, setLikedMusics] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchLikedMusics = async () => {
    try {
      const response = await axios.get('/api/musics/liked', { withCredentials: true });
      const list = Array.isArray(response.data) ? response.data : [];
      setLikedMusics(list.map((m) => ({ ...m, isLiked: true })));
    } catch (err) {
      console.error('보관함 목록 불러오기 실패:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLikedMusics();
  }, []);

  // 보관함에서 좋아요 취소 → 목록에서 즉시 제거
  const handleToggleLike = async (musicId) => {
    try {
      await axios.post(`/api/musics/${musicId}/like`, {}, { withCredentials: true });
      setLikedMusics((prev) => prev.filter((music) => music.id !== musicId));
    } catch (err) {
      console.error('좋아요 취소 실패:', err);
      alert('처리에 실패했습니다.');
    }
  };

  return (
    <>
      <Header searchTerm="" setSearchTerm={() => {}} />
      <div className="content-section">
        <div className="library-head">
          <div>
            <h2>📂 내 보관함</h2>
            <p>좋아요 표시한 곡이 보관되는 공간입니다.</p>
          </div>
          {!loading && likedMusics.length > 0 && (
            <span className="library-count">{likedMusics.length}곡</span>
          )}
        </div>

        {loading ? (
          <div className="lib-state">
            <i className="fa-solid fa-compact-disc fa-spin" />
            불러오는 중…
          </div>
        ) : likedMusics.length === 0 ? (
          <div className="lib-state">
            <i className="fa-solid fa-heart" />
            아직 좋아요 표시한 음악이 없습니다.
            <div className="lib-state-sub">곡 카드의 하트를 눌러 보관함에 담아보세요.</div>
          </div>
        ) : (
          <div className="library-grid">
            {likedMusics.map((music) => (
              <MusicCard
                key={music.id}
                music={music}
                onToggleLike={handleToggleLike}
              />
            ))}
          </div>
        )}
      </div>
    </>
  );
}
