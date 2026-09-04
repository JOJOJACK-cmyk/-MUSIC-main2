import React from 'react';
import { usePlayer } from '../context/PlayerContext';

export default function Top100Chart({ chartList = [], onSelectMusic }) {
  // 상위 100곡만
  const limitedChartList = Array.isArray(chartList) ? chartList.slice(0, 100) : [];

  const { currentTrack, isPlaying, togglePlay } = usePlayer();

  return (
    <div className="chart-wrap">
      {/* 상단 타이틀 영역 */}
      <div className="chart-head">
        <div>
          <h2>실시간 TOP 100</h2>
          <p>가장 사랑받는 인기 음악을 실시간으로 만나보세요.</p>
        </div>
        <div className="chart-head-meta">
          <span className="chart-count">총 {limitedChartList.length}곡</span>
          <span className="chart-live"><span className="chart-live-dot" /> 매분 자동 갱신</span>
        </div>
      </div>

      {/* 차트 리스트 */}
      <div className="chart-list">
        {limitedChartList.length > 0 ? (
          limitedChartList.map((music, index) => {
            const rank = index + 1;
            const isCurrent = currentTrack?.id === music.id;
            const playingNow = isCurrent && isPlaying;

            return (
              <div
                key={music.id || index}
                className={`chart-row ${playingNow ? 'is-playing' : ''} ${rank <= 3 ? 'is-top3' : ''}`}
                onClick={() => onSelectMusic(music)}
              >
                <div className="chart-row-left">
                  <span className={`chart-rank rank-${rank <= 3 ? rank : 'n'}`}>{rank}</span>

                  <div className="chart-thumb">
                    <img
                      src={music.thumbnailUrl || '/default-album.png'}
                      alt={music.title}
                      onError={(e) => { e.currentTarget.style.visibility = 'hidden'; }}
                    />
                    {playingNow && (
                      <div className="chart-eq" aria-hidden="true"><span /><span /><span /></div>
                    )}
                  </div>

                  <div className="chart-meta">
                    <p className="chart-title">{music.title}</p>
                    <p className="chart-artist">{music.artist}</p>
                  </div>
                </div>

                <button
                  className="chart-play"
                  onClick={(e) => {
                    e.stopPropagation();
                    isCurrent ? togglePlay() : onSelectMusic(music);
                  }}
                  aria-label={playingNow ? '일시정지' : '재생'}
                >
                  <i className={`fa-solid ${playingNow ? 'fa-pause' : 'fa-play'}`} />
                </button>
              </div>
            );
          })
        ) : (
          <p className="chart-empty">등록된 차트 데이터가 없습니다.</p>
        )}
      </div>
    </div>
  );
}
