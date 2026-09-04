import React, { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import Header from '../components/Header';
import { useNavigate } from 'react-router-dom';

export default function LivePage() {
  const videoRef = useRef(null);

  const [broadcasts, setBroadcasts] = useState([]);
  const [selectedBroadcast, setSelectedBroadcast] = useState(null);
  const [loading, setLoading] = useState(true);
  const [playerMessage, setPlayerMessage] = useState('');

  const liveApiUrl = '/api/broadcast/live';
    const navigate = useNavigate();

  // 1. 현재 방송 목록 조회
  useEffect(() => {
    const fetchLiveBroadcasts = async () => {
      try {
        const response = await fetch(liveApiUrl, {
          credentials: 'include',
        });

        if (!response.ok) {
          throw new Error('라이브 방송 목록 조회 실패');
        }

        const data = await response.json();

        setBroadcasts(data);

        if (data.length > 0) {
          setSelectedBroadcast((current) => {
            const stillLive = current
              ? data.find(
                  (broadcast) =>
                    broadcast.id === current.id
                )
              : null;

            return stillLive || data[0];
          });
        } else {
          setSelectedBroadcast(null);
        }
      } catch (error) {
        console.error(
          '라이브 방송 조회 실패:',
          error
        );

        setBroadcasts([]);
        setSelectedBroadcast(null);
      } finally {
        setLoading(false);
      }
    };

    // 최초 조회
    fetchLiveBroadcasts();

    // 3초마다 목록 갱신
    const interval = setInterval(
      fetchLiveBroadcasts,
      3000
    );

    return () => {
      clearInterval(interval);
    };
  }, []);

  // 2. 선택된 방송 HLS 재생
  useEffect(() => {
    const video = videoRef.current;

    if (!video || !selectedBroadcast) {
      return;
    }

    const hlsUrl = selectedBroadcast.hlsUrl;

    setPlayerMessage(
      '라이브 스트림 연결 중...'
    );

    // Chrome / Edge
    if (Hls.isSupported()) {
      const hls = new Hls();

      hls.loadSource(hlsUrl);
      hls.attachMedia(video);

      hls.on(
        Hls.Events.MANIFEST_PARSED,
        () => {
          setPlayerMessage('');

          video.play().catch(() => {
            console.log(
              '자동재생이 차단되었습니다.'
            );
          });
        }
      );

      hls.on(
        Hls.Events.ERROR,
        (event, data) => {
          console.error(
            'HLS 오류:',
            data
          );

          if (data.fatal) {
            setPlayerMessage(
              '라이브 영상을 불러오는 중 문제가 발생했습니다.'
            );
          }
        }
      );

      return () => {
        hls.destroy();
      };
    }

    // Safari
    if (
      video.canPlayType(
        'application/vnd.apple.mpegurl'
      )
    ) {
      const handleLoadedMetadata = () => {
        setPlayerMessage('');

        video.play().catch(() => {
          console.log(
            '자동재생이 차단되었습니다.'
          );
        });
      };

      video.src = hlsUrl;

      video.addEventListener(
        'loadedmetadata',
        handleLoadedMetadata
      );

      return () => {
        video.removeEventListener(
          'loadedmetadata',
          handleLoadedMetadata
        );

        video.pause();
        video.removeAttribute('src');
        video.load();
      };
    }

    setPlayerMessage(
      '이 브라우저에서는 HLS 재생을 지원하지 않습니다.'
    );
  }, [selectedBroadcast?.hlsUrl]);

  // 3. Redis 시청자 heartbeat
  useEffect(() => {
    if (!selectedBroadcast?.id) {
      return;
    }

    const sendHeartbeat = async () => {
      try {
        const response = await fetch(
          `/api/broadcast/${selectedBroadcast.id}/viewers/heartbeat`,
          {
            method: 'POST',
            credentials: 'include',
          }
        );

        if (!response.ok) {
          console.error(
            '시청자 heartbeat 실패:',
            response.status
          );
        }
      } catch (error) {
        console.error(
          '시청자 heartbeat 전송 실패:',
          error
        );
      }
    };

    // 방송 선택 즉시 한 번
    sendHeartbeat();

    // 이후 5초마다 heartbeat
    const interval = setInterval(
      sendHeartbeat,
      5000
    );

    return () => {
      clearInterval(interval);
    };
  }, [selectedBroadcast?.id]);

return (
  <>
    <Header searchTerm="" setSearchTerm={() => {}} />

    <div className="content-section live-page">
      {/* 페이지 제목 */}
      <div className="live-page-head">
        <h2><span className="live-dot" /> 실시간 스트리밍 라이브</h2>
        <p>현재 방송 중인 라이브 스트림을 감상해보세요.</p>
      </div>

      {/* 로딩 */}
      {loading && <div className="live-loading">방송 목록을 불러오는 중…</div>}

      {/* 방송 없음 */}
      {!loading && broadcasts.length === 0 && (
        <div className="live-empty">현재 진행 중인 라이브 방송이 없습니다.</div>
      )}

      {/* 대표 라이브 영역 */}
      {!loading && selectedBroadcast && (
        <>
          <section className={`live-stage ${broadcasts.length > 1 ? 'has-side' : ''}`}>
            {/* 대표 방송 */}
            <div className="live-main">
              <div className="live-video-box">
                <video ref={videoRef} controls autoPlay muted />

                <div className="live-badge"><span className="live-dot" /> LIVE</div>
                <div className="live-viewers">
                  <i className="fa-solid fa-user-group" /> {selectedBroadcast.viewerCount ?? 0}
                </div>
              </div>

              <div className="live-info">
                <h3>{selectedBroadcast.title}</h3>
                <div className="live-info-meta">
                  <span>{selectedBroadcast.broadcaster}</span>
                  <span>·</span>
                  <span><i className="fa-solid fa-user-group" /> {selectedBroadcast.viewerCount ?? 0}명 시청 중</span>
                </div>
                {playerMessage && <div className="live-msg">{playerMessage}</div>}
              </div>
            </div>

            {/* 오른쪽 추천 방송 */}
            {broadcasts.length > 1 && (
              <div className="live-side">
                <div className="live-side-title">다른 라이브</div>
                <div className="live-side-list">
                  {broadcasts
                    .filter((b) => b.id !== selectedBroadcast.id)
                    .slice(0, 4)
                    .map((broadcast) => (
                      <div
                        key={broadcast.id}
                        className="live-card"
                        onClick={() => navigate(`/live/${broadcast.id}`)}
                      >
                        <div className="live-thumb">
                          {broadcast.thumbnailUrl ? (
                            <img src={broadcast.thumbnailUrl} alt={broadcast.title} />
                          ) : (
                            <div className="live-thumb-empty">썸네일 없음</div>
                          )}
                          <div className="live-badge-sm"><span className="live-dot" /> LIVE</div>
                          <div className="live-viewers-sm">👥 {broadcast.viewerCount ?? 0}</div>
                        </div>
                        <div className="live-card-body">
                          <div className="live-card-title">{broadcast.title}</div>
                          <div className="live-card-host">{broadcast.broadcaster}</div>
                        </div>
                      </div>
                    ))}
                </div>
              </div>
            )}
          </section>

          {/* 전체 라이브 목록 */}
          <section className="live-all">
            <div className="live-all-head">
              <h3>🔥 현재 방송 중</h3>
              <span>{broadcasts.length}개 방송</span>
            </div>

            <div className="live-grid">
              {broadcasts.map((broadcast) => (
                <div
                  key={broadcast.id}
                  className="live-card"
                  onClick={() => navigate(`/live/${broadcast.id}`)}
                >
                  <div className={`live-thumb ${selectedBroadcast.id === broadcast.id ? 'is-selected' : ''}`}>
                    {broadcast.thumbnailUrl ? (
                      <img src={broadcast.thumbnailUrl} alt={broadcast.title} />
                    ) : (
                      <div className="live-thumb-empty">썸네일 없음</div>
                    )}
                    <div className="live-badge-sm"><span className="live-dot" /> LIVE</div>
                    <div className="live-viewers-sm">👥 {broadcast.viewerCount ?? 0}</div>
                  </div>

                  <div className="live-card-body">
                    <div className="live-card-title">{broadcast.title}</div>
                    <div className="live-card-host">{broadcast.broadcaster}</div>
                    <div className="live-card-viewers">👥 {broadcast.viewerCount ?? 0}명 시청 중</div>
                  </div>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  </>
);
}