import React from 'react';
import Header from '../components/Header';

export default function LibraryPage() {
  return (
    <main className="main-content">
      <Header searchTerm="" setSearchTerm={() => {}} />
      <div className="content-section">
        <h2>📂 내 보관함</h2>
        <p style={{ color: 'var(--text-sub)', marginTop: '12px' }}>
          좋아요 표시한 곡과 나만의 플레이리스트가 보관되는 공간입니다.
        </p>
      </div>
    </main>
  );
}
