import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import LivePoll from '../components/LivePoll';
import LiveChat from '../components/LiveChat';

/**
 * OBS 커스텀 브라우저 독(Dock)용 — 입력창까지 있는 실제 채팅창을 그대로 띄운다.
 *   URL: /live/:broadcastId/dock
 * ChatOverlay(/chat)와 달리 읽기 전용이 아니라 방송자가 직접 채팅을 치고 볼 수 있다.
 * 신청곡 투표창(songRequestEnabled)이 켜져 있으면 라이브 상세 페이지와 동일하게 함께 뜬다.
 * 화면 크롬(사이드바/헤더/플레이어바) 없이 렌더된다.
 */
export default function ChatDock() {
  const { broadcastId } = useParams();
  const { user } = useAuth();
  const [broadcast, setBroadcast] = useState(null);

  const currentUserName = user?.nickname || user?.name || user?.email?.split('@')[0] || '';
  const isBroadcaster =
    !!broadcast && !!currentUserName && broadcast.broadcaster === currentUserName;

  useEffect(() => {
    let cancelled = false;
    const fetchBroadcast = async () => {
      try {
        const res = await fetch('/api/broadcast/live', { credentials: 'include' });
        if (!res.ok) return;
        const data = await res.json();
        const found = data.find((item) => String(item.id) === String(broadcastId));
        if (!cancelled) setBroadcast(found || null);
      } catch (_) {}
    };
    fetchBroadcast();
    const interval = setInterval(fetchBroadcast, 3000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [broadcastId]);

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        background: '#0b0b0d',
        padding: 10,
        boxSizing: 'border-box',
      }}
    >
      {broadcast?.songRequestEnabled && (
        <LivePoll broadcastId={broadcastId} isBroadcaster={isBroadcaster} />
      )}
      <LiveChat broadcastId={broadcastId} />
    </div>
  );
}
