import React, { useState, useEffect } from 'react';
import Header from '../components/Header';
import axios from 'axios';

export default function LibraryPage() {
  const [likedMusics, setLikedMusics] = useState([]);
  const [loading, setLoading] = useState(true);

  // 내 보관함 좋아요 목록 불러오기
  const fetchLikedMusics = async () => {
    try {
      const response = await axios.get('/api/musics/liked', {
        withCredentials: true, // 쿠키/세션 인증 포함
      });
      setLikedMusics(response.data);
    } catch (err) {
      console.error('보관함 목록 불러오기 실패:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLikedMusics();
  }, []);

  // 보관함에서 바로 좋아요 취소(해제)하기
  const handleToggleLike = async (musicId) => {
    try {
      await axios.post(`/api/musics/${musicId}/like`, {}, {
        withCredentials: true,
      });
      // 성공 시 목록에서 즉시 제거
      setLikedMusics(likedMusics.filter((music) => music.id !== musicId));
    } catch (err) {
      console.error('좋아요 취소 실패:', err);
      alert('처리에 실패했습니다.');
    }
  };

  return (
    <main className="main-content">
      <Header searchTerm="" setSearchTerm={() => {}} />
      <div className="content-section">
        <h2>📂 내 보관함</h2>
        <p style={{ color: 'var(--text-sub)', marginTop: '12px', marginBottom: '24px' }}>
          좋아요 표시한 곡과 나만의 플레이리스트가 보관되는 공간입니다.
        </p>

        {loading ? (
          <p style={{ color: '#aaa' }}>불러오는 중...</p>
        ) : likedMusics.length === 0 ? (
          <p style={{ color: '#aaa' }}>아직 좋아요 표시한 음악이 없습니다.</p>
        ) : (
          <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
            {likedMusics.map((music) => (
              <div
                key={music.id}
                className="music-card"
                style={{
                  background: '#181818',
                  borderRadius: '8px',
                  padding: '12px',
                  width: '180px',
                  position: 'relative',
                }}
              >
                <img
                  src={music.thumbnailUrl}
                  alt={music.title}
                  style={{ width: '100%', aspectRatio: '16/9', objectFit: 'cover', borderRadius: '4px' }}
                />
                <h4 style={{ color: '#fff', fontSize: '14px', margin: '10px 0 5px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {music.title}
                </h4>
                <p style={{ color: '#aaa', fontSize: '12px', margin: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {music.artist}
                </p>
                {/* 채워진 하트 아이콘을 누르면 좋아요 취소 */}
                <button
                  onClick={() => handleToggleLike(music.id)}
                  style={{
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    position: 'absolute',
                    bottom: '12px',
                    right: '12px',
                    color: '#e91e63',
                    fontSize: '16px',
                  }}
                  title="좋아요 취소"
                >
                  <i className="fa-solid fa-heart"></i>
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}