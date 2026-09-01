import React, { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import Header from '../components/Header';

export default function LivePage() {
  const videoRef = useRef(null);

  const [broadcasts, setBroadcasts] = useState([]);
  const [selectedBroadcast, setSelectedBroadcast] = useState(null);
  const [loading, setLoading] = useState(true);
  const [playerMessage, setPlayerMessage] = useState('');

  const liveApiUrl = 'http://localhost:8080/api/broadcast/live';

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
          `http://localhost:8080/api/broadcast/${selectedBroadcast.id}/viewers/heartbeat`,
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
    <main className="main-content">
      <Header
        searchTerm=""
        setSearchTerm={() => {}}
      />

      <div className="content-section">
        <h2>
          📺 실시간 스트리밍 라이브
        </h2>

        <p
          style={{
            color: 'var(--text-sub)',
            marginTop: '12px',
          }}
        >
          현재 방송 중인 라이브 스트림을
          감상해보세요.
        </p>

        {/* 처음 로딩 */}
        {loading && (
          <div
            style={{
              marginTop: '30px',
            }}
          >
            방송 목록을 불러오는 중...
          </div>
        )}

        {/* 방송 없음 */}
        {!loading &&
          broadcasts.length === 0 && (
            <div
              style={{
                marginTop: '24px',
                maxWidth: '1000px',
                minHeight: '400px',
                backgroundColor: '#111',
                borderRadius: '12px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#aaa',
              }}
            >
              현재 진행 중인 라이브 방송이
              없습니다.
            </div>
          )}

        {/* 선택된 메인 방송 */}
        {!loading &&
          selectedBroadcast && (
            <div
              style={{
                marginTop: '24px',
                maxWidth: '1000px',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px',
                  marginBottom: '10px',
                }}
              >
                <span
                  style={{
                    fontWeight: 'bold',
                  }}
                >
                  🔴 LIVE
                </span>

                <span
                  style={{
                    fontSize: '18px',
                    fontWeight: 'bold',
                  }}
                >
                  {selectedBroadcast.title}
                </span>
              </div>

              <div
                style={{
                  marginBottom: '12px',
                  color: 'var(--text-sub)',
                }}
              >
                방송자:{' '}
                {selectedBroadcast.broadcaster}
                {' · '}
                👥{' '}
                {selectedBroadcast.viewerCount ??
                  0}
                명 시청 중
              </div>

              {playerMessage && (
                <div
                  style={{
                    marginBottom: '10px',
                    color: 'var(--text-sub)',
                  }}
                >
                  {playerMessage}
                </div>
              )}

              <video
                ref={videoRef}
                controls
                autoPlay
                muted
                style={{
                  width: '100%',
                  backgroundColor: '#000',
                  borderRadius: '12px',
                  aspectRatio: '16 / 9',
                }}
              />
            </div>
          )}

        {/* 현재 방송 목록 */}
        {broadcasts.length > 0 && (
          <div
            style={{
              marginTop: '40px',
              maxWidth: '1000px',
            }}
          >
            <h3
              style={{
                marginBottom: '16px',
              }}
            >
              현재 방송 중
            </h3>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns:
                  'repeat(auto-fill, minmax(220px, 1fr))',
                gap: '16px',
              }}
            >
              {broadcasts.map(
                (broadcast) => (
                  <div
                    key={broadcast.id}
                    onClick={() =>
                      setSelectedBroadcast(
                        broadcast
                      )
                    }
                    style={{
                      cursor: 'pointer',
                      backgroundColor:
                        '#181818',
                      borderRadius: '10px',
                      overflow: 'hidden',
                      border:
                        selectedBroadcast?.id ===
                        broadcast.id
                          ? '2px solid #ff2bbd'
                          : '2px solid transparent',
                    }}
                  >
                    {/* 방송 썸네일 */}
                    <div
                      style={{
                        height: '130px',
                        backgroundColor:
                          '#252525',
                        position: 'relative',
                        overflow: 'hidden',
                      }}
                    >
                      {broadcast.thumbnailUrl ? (
                        <img
                          src={
                            broadcast.thumbnailUrl
                          }
                          alt={broadcast.title}
                          style={{
                            width: '100%',
                            height: '100%',
                            objectFit: 'cover',
                            display: 'block',
                          }}
                        />
                      ) : (
                        <div
                          style={{
                            width: '100%',
                            height: '100%',
                            display: 'flex',
                            alignItems:
                              'center',
                            justifyContent:
                              'center',
                            color: '#888',
                          }}
                        >
                          썸네일 없음
                        </div>
                      )}

                      {/* LIVE */}
                      <div
                        style={{
                          position: 'absolute',
                          top: '8px',
                          left: '8px',
                          backgroundColor:
                            '#e91916',
                          color: '#fff',
                          fontSize: '12px',
                          fontWeight: 'bold',
                          padding: '4px 7px',
                          borderRadius: '5px',
                        }}
                      >
                        LIVE
                      </div>

                      {/* 시청자 수 */}
                      <div
                        style={{
                          position: 'absolute',
                          right: '8px',
                          bottom: '8px',
                          backgroundColor:
                            'rgba(0, 0, 0, 0.75)',
                          color: '#fff',
                          fontSize: '12px',
                          padding: '4px 7px',
                          borderRadius: '5px',
                        }}
                      >
                        👥{' '}
                        {broadcast.viewerCount ??
                          0}
                      </div>
                    </div>

                    {/* 방송 정보 */}
                    <div
                      style={{
                        padding: '12px',
                      }}
                    >
                      <div
                        style={{
                          fontWeight: 'bold',
                          marginBottom: '6px',
                        }}
                      >
                        {broadcast.title}
                      </div>

                      <div
                        style={{
                          color:
                            'var(--text-sub)',
                          fontSize: '14px',
                        }}
                      >
                        {broadcast.broadcaster}
                      </div>

                      <div
                        style={{
                          color:
                            'var(--text-sub)',
                          fontSize: '14px',
                          marginTop: '4px',
                        }}
                      >
                        👥{' '}
                        {broadcast.viewerCount ??
                          0}
                        명 시청 중
                      </div>
                    </div>
                  </div>
                )
              )}
            </div>
          </div>
        )}
      </div>
    </main>
  );
}