import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

/**
 * 진행 중인 라이브가 있으면 화면에 고정으로 뜨는 "LIVE" 버튼.
 * 누르면 현재 진행 중인 라이브로 이동한다.
 */
export default function LiveNowButton() {
  const [lives, setLives] = useState([]);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    let alive = true;
    const fetchLive = async () => {
      try {
        const res = await fetch('/api/broadcast/live', { credentials: 'include' });
        if (!res.ok) throw new Error();
        const data = await res.json();
        if (alive) setLives(Array.isArray(data) ? data : []);
      } catch (_) {
        if (alive) setLives([]);
      }
    };
    fetchLive();
    const t = setInterval(fetchLive, 15000);
    return () => { alive = false; clearInterval(t); };
  }, []);

  if (lives.length === 0) return null;

  // 시청자 많은 방송을 대표로
  const top = [...lives].sort((a, b) => (b.viewerCount || 0) - (a.viewerCount || 0))[0];

  // 이미 그 라이브를 보고 있으면 숨김
  if (location.pathname === `/live/${top.id}`) return null;

  return (
    <button
      className="live-now-btn"
      onClick={() => navigate(`/live/${top.id}`)}
      title={top.title}
    >
      <span className="live-dot" />
      LIVE
      {lives.length > 1 && (
        <span style={{ fontSize: 11, opacity: 0.85, fontWeight: 700 }}>+{lives.length - 1}</span>
      )}
    </button>
  );
}
