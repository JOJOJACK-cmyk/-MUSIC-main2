import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axiosInstance';

const uptime = (startedAt) => {
  if (!startedAt) return '';
  const m = Math.max(0, Math.floor((Date.now() - new Date(startedAt).getTime()) / 60000));
  return m < 60 ? `${m}분째` : `${Math.floor(m / 60)}시간 ${m % 60}분째`;
};

/** 모바일 라이브 목록: 큰 썸네일 카드가 세로로 */
export default function MobileLive() {
  const navigate = useNavigate();
  const [lives, setLives] = useState(null);

  useEffect(() => {
    const load = () => api.get('/api/broadcast/live')
      .then((r) => setLives(Array.isArray(r.data) ? r.data : []))
      .catch(() => setLives([]));
    load();
    const t = setInterval(load, 10_000);
    return () => clearInterval(t);
  }, []);

  return (
    <div className="m-page">
      <div className="m-page-head">
        <div>
          <h2>지금 방송 중</h2>
          <p>OBS 로 송출하는 실시간 방송 · 신청곡 투표와 음표</p>
        </div>
      </div>
      {lives === null ? (
        <div className="m-empty"><i className="fa-solid fa-tower-broadcast fa-fade" />불러오는 중…</div>
      ) : lives.length === 0 ? (
        <div className="m-empty"><i className="fa-solid fa-tower-broadcast" />지금은 방송 중인 채널이 없어요</div>
      ) : (
        <div className="m-live-list">
          {lives.map((b) => (
            <button key={b.id} className="m-live-item" onClick={() => navigate(`/live/${b.id}`)}>
              <div className="m-live-thumb big">
                {(b.snapshotUrl || b.thumbnailUrl)
                  ? <img src={b.snapshotUrl || b.thumbnailUrl} alt="" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                  : null}
                <span className="m-live-badge">LIVE</span>
                <span className="m-live-viewers"><i className="fa-solid fa-user-group" /> {b.viewerCount ?? 0}</span>
                {b.startedAt && <span className="m-live-uptime">{uptime(b.startedAt)}</span>}
              </div>
              <div className="m-live-info">
                <span className="m-live-av">{(b.broadcaster || '?').charAt(0)}</span>
                <span>
                  <strong>{b.title}</strong>
                  <small>{b.broadcaster}{b.category ? ` · ${b.category}` : ''}{b.songRequestEnabled ? ' · 🎵 신청곡 투표' : ''}</small>
                </span>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
