import React from 'react';
import Header from '../components/Header';

export default function LivePage() {
  return (
    <main className="main-content">
      <Header searchTerm="" setSearchTerm={() => {}} />
      <div className="content-section">
        <h2>📺 실시간 스트리밍 라이브</h2>
        <p style={{ color: 'var(--text-sub)', marginTop: '12px' }}>
          현재 방송 중인 고음질 라이브 스트림을 감상해보세요.
        </p>
      </div>
    </main>
  );
}
