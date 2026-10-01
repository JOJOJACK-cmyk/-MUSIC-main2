import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useLiveView } from '../context/LiveViewContext';

/**
 * 진행 중인 라이브가 있으면 화면에 고정으로 뜨는 "LIVE" 버튼.
 * 누르면 현재 진행 중인 라이브로 이동한다.
 */
export default function LiveNowButton() {
  const [lives, setLives] = useState([]);
  const location = useLocation();
  const navigate = useNavigate();
  const { browsingLive } = useLiveView() || {};

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

  // 이미 라이브 목록/상세 화면을 보고 있으면 중복이므로 숨김
  //  - 홈('/') : 음악/라이브 탭 상태와 무관하게 항상 숨김 (홈에는 '라이브' 탭이
  //    이미 있어서, 탭 전환 타이밍에 상관없이 겹칠 여지 자체를 없앤다)
  //  - 라이브 디렉터리(/live), 라이브 상세(/live/:id) 페이지
  if (
    browsingLive ||
    location.pathname === '/' ||
    location.pathname === '/live' ||
    location.pathname.startsWith('/live/')
  ) {
    return null;
  }

  // 시청자 많은 방송을 대표로
  const top = [...lives].sort((a, b) => (b.viewerCount || 0) - (a.viewerCount || 0))[0];

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
