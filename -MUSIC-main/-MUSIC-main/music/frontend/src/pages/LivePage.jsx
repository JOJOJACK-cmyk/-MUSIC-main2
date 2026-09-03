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
  <main className="main-content">
    <Header
      searchTerm=""
      setSearchTerm={() => {}}
    />

    <div
      className="content-section"
      style={{
        maxWidth: '1400px',
        margin: '0 auto',
        paddingBottom: '60px',
      }}
    >
      {/* 페이지 제목 */}
      <div style={{ marginBottom: '24px' }}>
        <h2>📺 실시간 스트리밍 라이브</h2>

        <p
          style={{
            color: 'var(--text-sub)',
            marginTop: '8px',
          }}
        >
          현재 방송 중인 라이브 스트림을 감상해보세요.
        </p>
      </div>

      {/* 로딩 */}
      {loading && (
        <div
          style={{
            padding: '80px 0',
            textAlign: 'center',
            color: 'var(--text-sub)',
          }}
        >
          방송 목록을 불러오는 중...
        </div>
      )}

      {/* 방송 없음 */}
      {!loading && broadcasts.length === 0 && (
        <div
          style={{
            minHeight: '450px',
            backgroundColor: '#111',
            borderRadius: '14px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#aaa',
          }}
        >
          현재 진행 중인 라이브 방송이 없습니다.
        </div>
      )}

      {/* ============================= */}
      {/* 대표 라이브 영역 */}
      {/* ============================= */}
      {!loading && selectedBroadcast && (
        <>
          <section
            style={{
              display: 'grid',
              gridTemplateColumns:
                broadcasts.length > 1
                  ? 'minmax(0, 3fr) minmax(260px, 1fr)'
                  : '1fr',
              gap: '18px',
            }}
          >
            {/* 대표 방송 */}
            <div>
              <div
                style={{
                  position: 'relative',
                  backgroundColor: '#000',
                  borderRadius: '14px',
                  overflow: 'hidden',
                }}
              >
                <video
                  ref={videoRef}
                  controls
                  autoPlay
                  muted
                  style={{
                    width: '100%',
                    aspectRatio: '16 / 9',
                    display: 'block',
                    backgroundColor: '#000',
                  }}
                />

                {/* LIVE 배지 */}
                <div
                  style={{
                    position: 'absolute',
                    top: '14px',
                    left: '14px',
                    padding: '5px 9px',
                    borderRadius: '5px',
                    backgroundColor: '#e91916',
                    color: '#fff',
                    fontSize: '12px',
                    fontWeight: 'bold',
                  }}
                >
                  LIVE
                </div>

                {/* 시청자 수 */}
                <div
                  style={{
                    position: 'absolute',
                    right: '14px',
                    top: '14px',
                    padding: '5px 9px',
                    borderRadius: '5px',
                    backgroundColor: 'rgba(0,0,0,0.75)',
                    color: '#fff',
                    fontSize: '12px',
                  }}
                >
                  👥 {selectedBroadcast.viewerCount ?? 0}명
                </div>
              </div>

              {/* 방송 정보 */}
              <div
                style={{
                  padding: '16px 4px',
                }}
              >
                <h3
                  style={{
                    fontSize: '21px',
                    marginBottom: '7px',
                  }}
                >
                  {selectedBroadcast.title}
                </h3>

                <div
                  style={{
                    color: 'var(--text-sub)',
                    fontSize: '14px',
                  }}
                >
                  {selectedBroadcast.broadcaster}
                  {' · '}
                  👥 {selectedBroadcast.viewerCount ?? 0}명 시청 중
                </div>

                {playerMessage && (
                  <div
                    style={{
                      color: 'var(--text-sub)',
                      marginTop: '8px',
                      fontSize: '13px',
                    }}
                  >
                    {playerMessage}
                  </div>
                )}
              </div>
            </div>

            {/* 오른쪽 추천 방송 */}
            {broadcasts.length > 1 && (
              <div>
                <h3
                  style={{
                    marginBottom: '12px',
                    fontSize: '16px',
                  }}
                >
                  다른 라이브
                </h3>

                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '12px',
                  }}
                >
                  {broadcasts
                    .filter(
                      (broadcast) =>
                        broadcast.id !== selectedBroadcast.id
                    )
                    .slice(0, 3)
                    .map((broadcast) => (
                      <div
                        key={broadcast.id}
                        onClick={() =>
                          navigate(`/live/${broadcast.id}`)
                        }
                        style={{
                          cursor: 'pointer',
                        }}
                      >
                        <div
                          style={{
                            position: 'relative',
                            aspectRatio: '16 / 9',
                            borderRadius: '10px',
                            overflow: 'hidden',
                            backgroundColor: '#222',
                          }}
                        >
                          {broadcast.thumbnailUrl ? (
                            <img
                              src={broadcast.thumbnailUrl}
                              alt={broadcast.title}
                              style={{
                                width: '100%',
                                height: '100%',
                                objectFit: 'cover',
                              }}
                            />
                          ) : (
                            <div
                              style={{
                                width: '100%',
                                height: '100%',
                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                                color: '#888',
                              }}
                            >
                              썸네일 없음
                            </div>
                          )}

                          <div
                            style={{
                              position: 'absolute',
                              top: '7px',
                              left: '7px',
                              backgroundColor: '#e91916',
                              padding: '3px 6px',
                              borderRadius: '4px',
                              color: '#fff',
                              fontSize: '11px',
                              fontWeight: 'bold',
                            }}
                          >
                            LIVE
                          </div>

                          <div
                            style={{
                              position: 'absolute',
                              right: '7px',
                              bottom: '7px',
                              backgroundColor: 'rgba(0,0,0,0.75)',
                              padding: '3px 6px',
                              borderRadius: '4px',
                              color: '#fff',
                              fontSize: '11px',
                            }}
                          >
                            👥 {broadcast.viewerCount ?? 0}
                          </div>
                        </div>

                        <div
                          style={{
                            marginTop: '6px',
                            fontWeight: 'bold',
                            fontSize: '14px',
                          }}
                        >
                          {broadcast.title}
                        </div>

                        <div
                          style={{
                            color: 'var(--text-sub)',
                            fontSize: '12px',
                          }}
                        >
                          {broadcast.broadcaster}
                        </div>
                      </div>
                    ))}
                </div>
              </div>
            )}
          </section>

          {/* ============================= */}
          {/* 전체 라이브 목록 */}
          {/* ============================= */}
          <section
            style={{
              marginTop: '42px',
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: '16px',
              }}
            >
              <h3>🔥 현재 방송 중</h3>

              <span
                style={{
                  color: 'var(--text-sub)',
                  fontSize: '13px',
                }}
              >
                {broadcasts.length}개 방송
              </span>
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns:
                  'repeat(auto-fill, minmax(240px, 1fr))',
                gap: '20px',
              }}
            >
              {broadcasts.map((broadcast) => (
                <div
                  key={broadcast.id}
                  onClick={() =>
                    navigate(`/live/${broadcast.id}`)
                  }
                  style={{
                    cursor: 'pointer',
                  }}
                >
                  {/* 썸네일 */}
                  <div
                    style={{
                      position: 'relative',
                      aspectRatio: '16 / 9',
                      borderRadius: '10px',
                      overflow: 'hidden',
                      backgroundColor: '#222',
                      border:
                        selectedBroadcast.id === broadcast.id
                          ? '2px solid #ff2bbd'
                          : '2px solid transparent',
                    }}
                  >
                    {broadcast.thumbnailUrl ? (
                      <img
                        src={broadcast.thumbnailUrl}
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
                          height: '100%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: '#888',
                        }}
                      >
                        썸네일 없음
                      </div>
                    )}

                    <div
                      style={{
                        position: 'absolute',
                        top: '8px',
                        left: '8px',
                        backgroundColor: '#e91916',
                        color: '#fff',
                        fontSize: '11px',
                        fontWeight: 'bold',
                        padding: '4px 7px',
                        borderRadius: '4px',
                      }}
                    >
                      LIVE
                    </div>

                    <div
                      style={{
                        position: 'absolute',
                        right: '8px',
                        bottom: '8px',
                        backgroundColor: 'rgba(0,0,0,0.75)',
                        color: '#fff',
                        fontSize: '11px',
                        padding: '4px 7px',
                        borderRadius: '4px',
                      }}
                    >
                      👥 {broadcast.viewerCount ?? 0}
                    </div>
                  </div>

                  {/* 카드 정보 */}
                  <div
                    style={{
                      paddingTop: '10px',
                    }}
                  >
                    <div
                      style={{
                        fontWeight: 'bold',
                        fontSize: '15px',
                        marginBottom: '4px',
                      }}
                    >
                      {broadcast.title}
                    </div>

                    <div
                      style={{
                        color: 'var(--text-sub)',
                        fontSize: '13px',
                      }}
                    >
                      {broadcast.broadcaster}
                    </div>

                    <div
                      style={{
                        color: 'var(--text-sub)',
                        fontSize: '12px',
                        marginTop: '3px',
                      }}
                    >
                      👥 {broadcast.viewerCount ?? 0}명 시청 중
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  </main>
);
}