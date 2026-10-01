import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header';
import { LIVE_CATEGORIES, normalizeCategory } from '../constants/liveCategories';

const liveApiUrl = '/api/broadcast/live';

// 방송자 이름 → 일관된 파스텔 색상 (레터 아바타용)
const AVATAR_COLORS = ['#E028B7', '#7C5CFF', '#2FB8FF', '#22C55E', '#F59E0B', '#FF5C7A', '#14B8A6'];
const colorFor = (name = '') => {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  return AVATAR_COLORS[h % AVATAR_COLORS.length];
};

const uptimeText = (startedAt) => {
  if (!startedAt) return null;
  const start = new Date(startedAt).getTime();
  if (Number.isNaN(start)) return null;
  let s = Math.max(0, Math.floor((Date.now() - start) / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  if (m > 0) return `${m}분째`;
  return '방금 시작';
};

function LetterAvatar({ name, size = 24 }) {
  const ch = (name || '?').trim().charAt(0).toUpperCase() || '?';
  return (
    <span
      className="live-av"
      style={{ width: size, height: size, background: colorFor(name || '?'), fontSize: size * 0.5 }}
    >
      {ch}
    </span>
  );
}

function LiveCard({ b, onClick }) {
  const up = uptimeText(b.startedAt);
  const cat = b.category ? normalizeCategory(b.category) : null;
  const thumb = b.snapshotUrl || b.thumbnailUrl;
  return (
    <article className="live-card live-card--dir" onClick={onClick}>
      <div className="live-thumb">
        {thumb ? (
          <img
            src={thumb}
            alt={b.title}
            loading="lazy"
            onError={(e) => {
              if (b.thumbnailUrl && e.currentTarget.src !== b.thumbnailUrl) {
                e.currentTarget.src = b.thumbnailUrl;
              } else {
                e.currentTarget.style.display = 'none';
              }
            }}
          />
        ) : (
          <div className="live-thumb-empty">
            <i className="fa-solid fa-tower-broadcast" style={{ fontSize: 22, opacity: 0.5 }} />
          </div>
        )}
        <div className="live-badge-sm"><span className="live-dot" /> LIVE</div>
        <div className="live-viewers-sm">
          <i className="fa-solid fa-user-group" style={{ marginRight: 4 }} />
          {(b.viewerCount ?? 0).toLocaleString()}
        </div>
        {up && <div className="live-uptime">{up}</div>}
      </div>

      <div className="live-card-body live-card-body--dir">
        <LetterAvatar name={b.broadcaster} size={34} />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div className="live-card-title" title={b.title}>{b.title || '제목 없는 방송'}</div>
          <div className="live-card-host">{b.broadcaster || '익명 방송자'}</div>
          {cat && <span className="live-cat-pill">{cat}</span>}
        </div>
      </div>
    </article>
  );
}

export default function LivePage() {
  const navigate = useNavigate();
  const [broadcasts, setBroadcasts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [cat, setCat] = useState('전체');

  useEffect(() => {
    let alive = true;
    const fetchLive = async () => {
      try {
        const res = await fetch(liveApiUrl, { credentials: 'include' });
        if (!res.ok) throw new Error('라이브 목록 조회 실패');
        const data = await res.json();
        if (alive) setBroadcasts(Array.isArray(data) ? data : []);
      } catch (e) {
        console.error('라이브 방송 조회 실패:', e);
        if (alive) setBroadcasts([]);
      } finally {
        if (alive) setLoading(false);
      }
    };
    fetchLive();
    const t = setInterval(fetchLive, 8000);
    return () => { alive = false; clearInterval(t); };
  }, []);

  const totalViewers = useMemo(
    () => broadcasts.reduce((sum, b) => sum + (b.viewerCount || 0), 0),
    [broadcasts]
  );

  // 실제로 존재하는 카테고리만 칩으로 노출
  const usedCategories = useMemo(() => {
    const set = new Set(broadcasts.map((b) => normalizeCategory(b.category)));
    return LIVE_CATEGORIES.filter((c) => set.has(c));
  }, [broadcasts]);

  const shown = cat === '전체'
    ? broadcasts
    : broadcasts.filter((b) => normalizeCategory(b.category) === cat);

  // 시청자 많은 순
  const sorted = [...shown].sort((a, b) => (b.viewerCount || 0) - (a.viewerCount || 0));

  return (
    <>
      <Header searchTerm="" setSearchTerm={() => {}} />

      <div className="content-section live-page">
        <div className="live-page-head">
          <h2><span className="live-dot" /> 실시간 스트리밍 라이브</h2>
          <p>
            {loading
              ? '방송 목록을 불러오는 중…'
              : broadcasts.length > 0
                ? `지금 ${broadcasts.length.toLocaleString()}개 방송 · 시청자 ${totalViewers.toLocaleString()}명`
                : '현재 방송 중인 라이브가 없습니다.'}
          </p>
        </div>

        {!loading && broadcasts.length > 0 && (
          <div className="live-chips">
            <button
              className={`live-chip ${cat === '전체' ? 'is-active' : ''}`}
              onClick={() => setCat('전체')}
            >
              전체 <span>{broadcasts.length}</span>
            </button>
            {usedCategories.map((c) => (
              <button
                key={c}
                className={`live-chip ${cat === c ? 'is-active' : ''}`}
                onClick={() => setCat(c)}
              >
                {c}
                <span>{broadcasts.filter((b) => normalizeCategory(b.category) === c).length}</span>
              </button>
            ))}
          </div>
        )}

        {loading ? (
          <div className="live-loading">방송 목록을 불러오는 중…</div>
        ) : broadcasts.length === 0 ? (
          <div className="live-empty">
            <div style={{ textAlign: 'center' }}>
              <i className="fa-solid fa-satellite-dish" style={{ fontSize: 30, display: 'block', marginBottom: 12, opacity: 0.6 }} />
              현재 진행 중인 라이브 방송이 없습니다.
              <div style={{ fontSize: 12, marginTop: 6, opacity: 0.75 }}>
                프로필 → 스튜디오에서 방송을 시작하면 여기에 노출됩니다.
              </div>
            </div>
          </div>
        ) : sorted.length === 0 ? (
          <div className="live-loading">‘{cat}’ 카테고리에 진행 중인 방송이 없습니다.</div>
        ) : (
          <div className="live-grid">
            {sorted.map((b) => (
              <LiveCard key={b.id} b={b} onClick={() => navigate(`/live/${b.id}`)} />
            ))}
          </div>
        )}
      </div>
    </>
  );
}
