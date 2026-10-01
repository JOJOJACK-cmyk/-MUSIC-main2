import React, { useEffect, useState } from 'react';
import api from '../../api/axiosInstance';
import { usePlayer } from '../../context/PlayerContext';
import TrackRow, { toTrack } from '../../components/mobile/TrackRow';

/** 모바일 TOP 100: 한 줄짜리 목록 + 전체 재생 */
export default function MobileChart() {
  const { selectTrack } = usePlayer();
  const [chart, setChart] = useState(null);

  useEffect(() => {
    const load = () => api.get('/api/chart/realtime')
      .then((r) => setChart((Array.isArray(r.data) ? r.data : []).map(toTrack)))
      .catch(() => setChart([]));
    load();
    const t = setInterval(load, 60_000); // 데스크톱과 같이 1분마다 갱신
    return () => clearInterval(t);
  }, []);

  return (
    <div className="m-page">
      <div className="m-page-head">
        <div>
          <h2>실시간 TOP 100</h2>
          <p>최근 24시간 청취 기준 · 1분마다 갱신</p>
        </div>
        <button className="m-btn primary small" disabled={!chart?.length} onClick={() => chart?.length && selectTrack(chart[0], chart)}>
          <i className="fa-solid fa-play" /> 전체 재생
        </button>
      </div>
      {chart === null ? (
        <div className="m-empty"><i className="fa-solid fa-compact-disc fa-spin" />불러오는 중…</div>
      ) : chart.length === 0 ? (
        <div className="m-empty"><i className="fa-solid fa-chart-line" />차트가 비어 있어요</div>
      ) : (
        <div className="m-list">
          {chart.map((t, i) => <TrackRow key={t.id} track={t} queue={chart} rank={i + 1} />)}
        </div>
      )}
    </div>
  );
}
