import React, { useState, useEffect } from 'react';
import Header from '../components/Header';
import MusicCard from '../components/MusicCard';
import axios from 'axios';

export default function LibraryPage() {
  const [likedMusics, setLikedMusics] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const fetchLikedMusics = async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await axios.get('/api/musics/liked', { withCredentials: true });
      const list = Array.isArray(response.data) ? response.data : [];
      setLikedMusics(list.map((m) => ({ ...m, isLiked: true })));
    } catch (err) {
      console.error('보관함 목록 불러오기 실패:', err);
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLikedMusics();
  }, []);

  // 보관함에서 좋아요 취소 → 목록에서 즉시 제거 (서버 응답 기준)
  const handleToggleLike = async (musicId) => {
    try {
      const res = await axios.post(`/api/musics/${musicId}/like`, {}, { withCredentials: true });
      const liked = res?.data?.liked;
      // 보관함에서는 좋아요 취소가 기본 동작 → 취소되면 목록에서 제거
      if (liked === false || liked === undefined) {
        setLikedMusics((prev) => prev.filter((music) => music.id !== musicId));
      }
      return res?.data;
    } catch (err) {
      console.error('좋아요 취소 실패:', err);
      alert('처리에 실패했습니다.');
      throw err;
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
          {!loading && !error && likedMusics.length > 0 && (
            <span className="library-count">{likedMusics.length}곡</span>
          )}
        </div>

        {loading ? (
          <div className="lib-state">
            <i className="fa-solid fa-compact-disc fa-spin" />
            불러오는 중…
          </div>
        ) : error ? (
          <div className="lib-state">
            <i className="fa-solid fa-triangle-exclamation" />
            보관함을 불러오지 못했습니다.
            <div className="lib-state-sub">로그인 상태를 확인한 뒤 다시 시도해 주세요.</div>
            <button
              onClick={fetchLikedMusics}
              style={{
                marginTop: 12, padding: '8px 16px', borderRadius: 8, border: '1px solid #412a3c',
                background: 'transparent', color: '#d9c7d4', cursor: 'pointer', fontWeight: 600,
              }}
            >
              다시 시도
            </button>
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
                queue={likedMusics}
                onToggleLike={handleToggleLike}
              />
            ))}
          </div>
        )}
      </div>
    </>
  );
}
