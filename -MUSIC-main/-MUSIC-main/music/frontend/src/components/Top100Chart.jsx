import React from 'react';

export default function Top100Chart({ chartList = [], onSelectMusic }) {
  // 💡 100곡을 초과하지 않도록 엄격하게 상위 100개만 슬라이싱
  const limitedChartList = Array.isArray(chartList) ? chartList.slice(0, 100) : [];

  return (
    <div style={{ width: '100%', maxWidth: '900px', margin: '0 auto', color: '#fff' }}>
      {/* 상단 타이틀 영역 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '24px', paddingBottom: '12px', borderBottom: '1px solid rgba(255, 255, 255, 0.1)' }}>
        <div>
          <h2 style={{ fontSize: '24px', fontWeight: '800', letterSpacing: '-0.5px', margin: '0 0 4px 0' }}>실시간 TOP 100</h2>
          <p style={{ fontSize: '13px', color: '#a1a1aa', margin: '0' }}>가장 사랑받는 인기 음악을 실시간으로 만나보세요.</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '12px', color: '#a1a1aa', fontWeight: '500' }}>
            총 {limitedChartList.length}곡
          </span>
          <span style={{ fontSize: '12px', color: '#ec4899', fontWeight: '600', backgroundColor: 'rgba(236, 72, 153, 0.1)', padding: '4px 10px', borderRadius: '20px' }}>
            매분 자동 갱신
          </span>
        </div>
      </div>

      {/* 차트 리스트 래퍼 */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {limitedChartList.length > 0 ? (
          limitedChartList.map((music, index) => {
            const isTop3 = index < 3;
            return (
              <div
                key={music.id || index}
                onClick={() => onSelectMusic(music)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '12px 18px',
                  backgroundColor: 'rgba(255, 255, 255, 0.03)',
                  backdropFilter: 'blur(10px)',
                  borderRadius: '14px',
                  cursor: 'pointer',
                  border: '1px solid rgba(255, 255, 255, 0.06)',
                  transition: 'all 0.2s ease-in-out'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.backgroundColor = 'rgba(236, 72, 153, 0.08)';
                  e.currentTarget.style.borderColor = 'rgba(236, 72, 153, 0.3)';
                  e.currentTarget.style.transform = 'translateY(-2px)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.03)';
                  e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.06)';
                  e.currentTarget.style.transform = 'translateY(0)';
                }}
              >
                {/* 좌측: 순위 + 썸네일 + 곡 정보 */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '18px', overflow: 'hidden' }}>
                  {/* 순위 (TOP 3는 핫핑크 컬러 강조) */}
                  <span style={{
                    width: '28px',
                    textAlign: 'center',
                    fontWeight: '800',
                    fontSize: '16px',
                    color: isTop3 ? '#ec4899' : '#71717a'
                  }}>
                    {index + 1}
                  </span>

                  {/* 앨범 썸네일 */}
                  <div style={{ position: 'relative', width: '52px', height: '52px', borderRadius: '10px', overflow: 'hidden', flexShrink: '0', boxShadow: '0 4px 10px rgba(0,0,0,0.3)' }}>
                    <img
                      src={music.thumbnailUrl || '/default-album.png'}
                      alt={music.title}
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  </div>

                  {/* 곡 제목 및 아티스트 */}
                  <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', overflow: 'hidden' }}>
                    <p style={{ fontWeight: '600', fontSize: '15px', color: '#f4f4f5', margin: '0 0 4px 0', whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                      {music.title}
                    </p>
                    <p style={{ fontSize: '13px', color: '#a1a1aa', margin: '0', whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                      {music.artist}
                    </p>
                  </div>
                </div>

                {/* 우측: 재생 버튼 */}
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelectMusic(music);
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    padding: '8px 16px',
                    backgroundColor: '#db2777',
                    color: '#fff',
                    border: 'none',
                    borderRadius: '20px',
                    cursor: 'pointer',
                    fontWeight: '700',
                    fontSize: '13px',
                    boxShadow: '0 4px 12px rgba(219, 39, 119, 0.4)',
                    transition: 'background 0.2s, transform 0.1s',
                    flexShrink: '0'
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#be185d'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#db2777'}
                  onMouseDown={(e) => e.currentTarget.style.transform = 'scale(0.95)'}
                  onMouseUp={(e) => e.currentTarget.style.transform = 'scale(1)'}
                >
                  <span>▶</span> 재생
                </button>
              </div>
            );
          })
        ) : (
          <p style={{ textAlign: 'center', color: '#71717a', padding: '60px 0' }}>등록된 차트 데이터가 없습니다.</p>
        )}
      </div>
    </div>
  );
}