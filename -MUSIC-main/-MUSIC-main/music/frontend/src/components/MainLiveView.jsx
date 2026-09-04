import React, { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { LIVE_CATEGORIES, normalizeCategory } from '../constants/liveCategories';

function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function LiveCard({ b, onClick }) {
  return (
    <div className="live-card" onClick={onClick}>
      <div className="live-thumb">
        {b.thumbnailUrl ? (
          <img src={b.thumbnailUrl} alt={b.title} />
        ) : (
          <div className="live-thumb-empty">썸네일 없음</div>
        )}
        <div className="live-badge-sm"><span className="live-dot" /> LIVE</div>
        <div className="live-viewers-sm">👥 {b.viewerCount ?? 0}</div>
      </div>
      <div className="live-card-body">
        <div className="live-card-title">{b.title}</div>
        <div className="live-card-host">{b.broadcaster}</div>
      </div>
    </div>
  );
}

export default function MainLiveView({ broadcasts = [] }) {
  const navigate = useNavigate();
  const go = (id) => navigate(`/live/${id}`);

  // 라이브 목록이 바뀔 때만 다시 섞는다 (렌더마다 흔들리지 않게)
  const idsKey = broadcasts.map((b) => b.id).sort().join(',');

  const recommended = useMemo(
    () => shuffle(broadcasts).slice(0, 6),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [idsKey]
  );

  // 카테고리별 그룹핑
  const grouped = useMemo(() => {
    const map = {};
    for (const b of broadcasts) {
      const key = normalizeCategory(b.category);
      (map[key] ||= []).push(b);
    }
    return map;
  }, [idsKey]); // eslint-disable-line react-hooks/exhaustive-deps

  if (broadcasts.length === 0) {
    return (
      <div className="lib-state" style={{ marginTop: 8 }}>
        <i className="fa-solid fa-satellite-dish" />
        지금 진행 중인 라이브가 없어요.
        <div className="lib-state-sub">프로필 → 내 채널에서 방송을 시작하면 여기에 노출됩니다.</div>
      </div>
    );
  }

  return (
    <>
      {/* 추천 라이브 (랜덤) */}
      <section className="content-section">
        <div className="section-header">
          <h2>🎲 추천 라이브</h2>
          <span style={{ color: 'var(--text-sub)', fontSize: 14 }}>랜덤 추천</span>
        </div>
        <div className="live-grid">
          {recommended.map((b) => (
            <LiveCard key={b.id} b={b} onClick={() => go(b.id)} />
          ))}
        </div>
      </section>

      {/* 카테고리별 진행 중인 라이브 */}
      {LIVE_CATEGORIES.filter((c) => grouped[c]?.length).map((cat) => (
        <section className="content-section" key={cat} style={{ marginTop: 40 }}>
          <div className="section-header">
            <h2>{cat}</h2>
            <span style={{ color: 'var(--text-sub)', fontSize: 14 }}>{grouped[cat].length}개 방송</span>
          </div>
          <div className="live-grid">
            {grouped[cat].map((b) => (
              <LiveCard key={b.id} b={b} onClick={() => go(b.id)} />
            ))}
          </div>
        </section>
      ))}
    </>
  );
}
