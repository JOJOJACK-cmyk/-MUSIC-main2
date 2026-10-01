import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../../api/axiosInstance';
import { usePlayer } from '../../context/PlayerContext';
import TrackRow, { toTrack } from '../../components/mobile/TrackRow';

const GENRES = [
  { key: 'KPOP', label: 'K-POP' },
  { key: 'JPOP', label: 'J-POP' },
  { key: 'POP', label: 'POP' },
  { key: 'VTUBER', label: 'VTuber' },
];

// 장르 섹션: 인기차트 순위 → 조회수 순, 같은 아티스트는 3곡까지
function pickGenre(list, genre) {
  const perArtist = {};
  return list
    .filter((m) => (m.genre || 'POP').toUpperCase() === genre && !/\s*-\s*topic\s*$/i.test(m.artist || ''))
    .sort((a, b) => ((a.trendingRank ?? 9999) - (b.trendingRank ?? 9999)) || ((Number(b.viewCount) || 0) - (Number(a.viewCount) || 0)))
    .filter((m) => {
      const k = (m.artist || '').toLowerCase();
      perArtist[k] = (perArtist[k] || 0) + 1;
      return perArtist[k] <= 3;
    })
    .slice(0, 12)
    .map(toTrack);
}

/** 가로로 넘기는 곡 카드 줄 */
function HRail({ title, more, tracks }) {
  const { selectTrack, currentTrack, isPlaying } = usePlayer();
  if (!tracks.length) return null;
  return (
    <section className="m-section">
      <div className="m-section-head">
        <h2>{title}</h2>
        {more && <Link to={more}>전체 <i className="fa-solid fa-chevron-right" /></Link>}
      </div>
      <div className="m-rail">
        {tracks.map((t) => {
          const playing = currentTrack && String(currentTrack.id) === String(t.id) && isPlaying;
          return (
            <button key={t.id} className="m-card" onClick={() => selectTrack(t, tracks)}>
              <div className="m-card-art">
                {t.thumbnailUrl ? <img src={t.thumbnailUrl} alt="" loading="lazy" /> : <i className="fa-solid fa-music" />}
                <span className="m-card-play"><i className={`fa-solid ${playing ? 'fa-pause' : 'fa-play'}`} /></span>
              </div>
              <strong>{t.title}</strong>
              <small>{t.artist}</small>
            </button>
          );
        })}
      </div>
    </section>
  );
}

export default function MobileHome() {
  const navigate = useNavigate();
  const { selectTrack } = usePlayer();
  const [musics, setMusics] = useState([]);
  const [chart, setChart] = useState([]);
  const [lives, setLives] = useState([]);

  useEffect(() => {
    api.get('/api/musics').then((r) => setMusics(Array.isArray(r.data) ? r.data : [])).catch(() => {});
    api.get('/api/chart/realtime').then((r) => setChart((Array.isArray(r.data) ? r.data : []).map(toTrack))).catch(() => {});
    api.get('/api/broadcast/live').then((r) => setLives(Array.isArray(r.data) ? r.data : [])).catch(() => {});
  }, []);

  const trending = chart.slice(0, 10);
  const genres = useMemo(() => GENRES.map((g) => ({ ...g, tracks: pickGenre(musics, g.key) })), [musics]);

  return (
    <div className="m-page">
      <div className="m-hero">
        <span className="m-hero-tag">● TODAY'S PICK</span>
        <h2>지금 가장 많이 듣는 곡</h2>
        <p>최근 24시간 청취 기준 실시간 차트</p>
        <div className="m-hero-actions">
          <button className="m-btn primary" disabled={!trending.length} onClick={() => trending.length && selectTrack(trending[0], chart)}>
            <i className="fa-solid fa-play" /> 바로 듣기
          </button>
          <button className="m-btn ghost" onClick={() => navigate('/charts')}>차트 보기</button>
        </div>
        <div className="m-hero-notes" aria-hidden="true">♪ ♫ ♬</div>
      </div>

      <HRail title="실시간 인기 급상승" more="/charts" tracks={trending} />

      {lives.length > 0 && (
        <section className="m-section">
          <div className="m-section-head">
            <h2><span className="m-live-dot" /> 지금 라이브</h2>
            <Link to="/live">전체 <i className="fa-solid fa-chevron-right" /></Link>
          </div>
          <div className="m-rail">
            {lives.map((b) => (
              <button key={b.id} className="m-live-card" onClick={() => navigate(`/live/${b.id}`)}>
                <div className="m-live-thumb">
                  {(b.snapshotUrl || b.thumbnailUrl)
                    ? <img src={b.snapshotUrl || b.thumbnailUrl} alt="" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                    : null}
                  <span className="m-live-badge">LIVE</span>
                  <span className="m-live-viewers"><i className="fa-solid fa-user-group" /> {b.viewerCount ?? 0}</span>
                </div>
                <strong>{b.title}</strong>
                <small>{b.broadcaster}</small>
              </button>
            ))}
          </div>
        </section>
      )}

      {chart.length > 0 && (
        <section className="m-section">
          <div className="m-section-head">
            <h2>TOP 5</h2>
            <Link to="/charts">TOP 100 <i className="fa-solid fa-chevron-right" /></Link>
          </div>
          <div className="m-list">
            {chart.slice(0, 5).map((t, i) => <TrackRow key={t.id} track={t} queue={chart} rank={i + 1} />)}
          </div>
        </section>
      )}

      {genres.map((g) => <HRail key={g.key} title={`${g.label} 인기곡`} tracks={g.tracks} />)}
    </div>
  );
}
