import React, { useEffect, useState } from 'react';
import Header from '../components/Header';
import Top100Chart from '../components/Top100Chart'; // 👈 차트 UI 컴포넌트 임포트
import { musicApi } from '../api/musicApi';         // 👈 musicApi 임포트
import { usePlayer } from '../context/PlayerContext'; // 👈 플레이어 컨텍스트 임포트

export default function ChartPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [chartList, setChartList] = useState([]);
  const [loading, setLoading] = useState(true);
  const { playTrack } = usePlayer();

  // 1. 컴포넌트 마운트 시 실시간 TOP 100 차트 데이터 호출
  useEffect(() => {
    const fetchChartData = async () => {
      try {
        setLoading(true);
        const response = await musicApi.getTop100Chart();
        // 백엔드 응답 구조에 맞춰 데이터 세팅 (response.data 또는 response)
        setChartList(response.data || response);
      } catch (error) {
        console.error('TOP 100 차트 데이터를 불러오는 데 실패했습니다:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchChartData();
  }, []);

  // 2. 차트에서 곡 선택 시 재생
  const handleSelectMusic = (music) => {
    playTrack(music);
  };

  return (
    <main className="main-content">
      <Header searchTerm={searchTerm} setSearchTerm={setSearchTerm} />

      <div className="content-section">
        <h2>🔥 TOP 100 차트</h2>

        {loading ? (
          <p style={{ color: 'var(--text-sub)', marginTop: '12px' }}>
            실시간 음원 차트 TOP 100을 불러오는 중입니다...
          </p>
        ) : (
          <div style={{ marginTop: '20px' }}>
            {/* 리스트가 비어있을 때의 예외 처리 및 차트 컴포넌트 렌더링 */}
            <Top100Chart chartList={chartList} onSelectMusic={handleSelectMusic} />
          </div>
        )}
      </div>
    </main>
  );
}