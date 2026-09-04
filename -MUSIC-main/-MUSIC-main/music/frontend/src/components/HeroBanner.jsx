import React, { useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePlayer } from '../context/PlayerContext';

export default function HeroBanner({ trending = [] }) {
  const navigate = useNavigate();
  const { playlist, playTrack } = usePlayer();
  const lastIdRef = useRef(null);

  const handlePlay = () => {
    // 실시간 인기 급상승 곡에서 랜덤 재생 (없으면 전체 목록에서)
    const pool = (trending && trending.length > 0 ? trending : playlist).filter(
      (t) => t && t.youtubeVideoId
    );
    if (pool.length === 0) return;

    // 직전에 튼 곡은 가급적 피해서 뽑기
    let candidates = pool.filter((t) => t.id !== lastIdRef.current);
    if (candidates.length === 0) candidates = pool;

    const pick = candidates[Math.floor(Math.random() * candidates.length)];
    lastIdRef.current = pick.id;
    playTrack(pick);
  };

  return (
    <section className="hero-banner">
      <div className="hero-orb hero-orb--1" />
      <div className="hero-orb hero-orb--2" />

      <div className="hero-content">
        <div className="banner-text">
          <span className="badge">
            <span className="badge-dot" />
            TODAY&apos;S PICK
          </span>
          <h1>
            실시간 인기 스트리밍 음악을<br />
            지금 바로 감상해보세요
          </h1>
          <p>지금 가장 많이 듣는 곡을 실시간 차트로 만나보세요</p>

          <div className="hero-actions">
            <button type="button" className="hero-cta" onClick={handlePlay}>
              <i className="fa-solid fa-play" />
              지금 듣기
            </button>
            <button
              type="button"
              className="hero-link"
              onClick={() => navigate('/charts')}
              style={{ background: 'none', border: 'none', cursor: 'pointer' }}
            >
              차트 전체보기
              <i className="fa-solid fa-arrow-right" />
            </button>
          </div>
        </div>

        <div className="hero-visual" aria-hidden="true">
          <div className="hero-disc">
            <i className="fa-solid fa-compact-disc" />
          </div>
          <div className="hero-eq">
            <span /><span /><span /><span /><span />
          </div>
        </div>
      </div>
    </section>
  );
}
