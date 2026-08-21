import React from 'react';
import Header from '../components/Header';

export default function ChartPage() {
  return (
    <main className="main-content">
      <Header searchTerm="" setSearchTerm={() => {}} />
      <div className="content-section">
        <h2>🔥 TOP 100 차트</h2>
        <p style={{ color: 'var(--text-sub)', marginTop: '12px' }}>
          실시간 음원 차트 TOP 100을 집계 중입니다.
        </p>
      </div>
    </main>
  );
}
