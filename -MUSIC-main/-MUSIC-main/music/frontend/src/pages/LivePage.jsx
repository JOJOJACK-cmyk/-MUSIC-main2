import React, { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import Header from '../components/Header';

export default function LivePage() {
  const videoRef = useRef(null);

  // Spring Boot가 알려주는 실제 방송 상태
  const [liveStatus, setLiveStatus] = useState('CHECKING');

  // 영상 플레이어 상태 메시지
  const [playerMessage, setPlayerMessage] = useState('');

  const hlsUrl = 'http://localhost:8081/live/livestream.m3u8';
  const liveStatusUrl = 'http://localhost:8080/api/live/status';

  // 1. Spring Boot를 통해 OBS/SRS 방송 상태 확인
  useEffect(() => {
    const checkLiveStatus = async () => {
      try {
        const response = await fetch(liveStatusUrl, {
          credentials: 'include',
        });

        if (!response.ok) {
          throw new Error('라이브 상태 조회 실패');
        }

        const data = await response.json();

        setLiveStatus(data.status);
      } catch (error) {
        console.error('라이브 상태 확인 실패:', error);
        setLiveStatus('SRS_DOWN');
      }
    };

    // 페이지 들어오자마자 바로 한 번 확인
    checkLiveStatus();

    // 이후 3초마다 확인
    const interval = setInterval(checkLiveStatus, 3000);

    return () => {
      clearInterval(interval);
    };
  }, []);

  // 2. 실제 방송이 LIVE일 때만 HLS 플레이어 연결
  useEffect(() => {
    const video = videoRef.current;

    if (!video) return;

    // 방송 중이 아니면 기존 영상 연결 제거
    if (liveStatus !== 'LIVE') {
      video.pause();
      video.removeAttribute('src');
      video.load();

      setPlayerMessage('');
      return;
    }

    setPlayerMessage('라이브 스트림 연결 중...');

    // Chrome / Edge
    if (Hls.isSupported()) {
      const hls = new Hls();

      hls.loadSource(hlsUrl);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        setPlayerMessage('');

        video.play().catch(() => {
          console.log('브라우저 자동재생이 차단되었습니다.');
        });
      });

      hls.on(Hls.Events.ERROR, (event, data) => {
        console.error('HLS 오류:', data);

        if (data.fatal) {
          setPlayerMessage('라이브 영상을 불러오는 중 문제가 발생했습니다.');
        }
      });

      return () => {
        hls.destroy();
      };
    }

    // Safari 등 자체 HLS 지원 브라우저
    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      const handleLoadedMetadata = () => {
        setPlayerMessage('');

        video.play().catch(() => {
          console.log('브라우저 자동재생이 차단되었습니다.');
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
  }, [liveStatus]);

  return (
    <main className="main-content">
      <Header
        searchTerm=""
        setSearchTerm={() => {}}
      />

      <div className="content-section">
        <h2>📺 실시간 스트리밍 라이브</h2>

        <p
          style={{
            color: 'var(--text-sub)',
            marginTop: '12px',
          }}
        >
          현재 방송 중인 라이브 스트림을 감상해보세요.
        </p>

        <div
          style={{
            marginTop: '24px',
            maxWidth: '1000px',
          }}
        >
          {/* 방송 상태 */}
          <div
            style={{
              marginBottom: '12px',
              fontWeight: 'bold',
            }}
          >
            {liveStatus === 'CHECKING' &&
              '방송 상태 확인 중...'}

            {liveStatus === 'LIVE' &&
              '🔴 LIVE'}

            {liveStatus === 'OFFLINE' &&
              '현재 방송 중이 아닙니다.'}

            {liveStatus === 'SRS_DOWN' &&
              '스트리밍 서버에 연결할 수 없습니다.'}
          </div>

          {/* LIVE일 때 실제 영상 */}
          {liveStatus === 'LIVE' && (
            <>
              {playerMessage && (
                <p
                  style={{
                    marginBottom: '10px',
                    color: 'var(--text-sub)',
                  }}
                >
                  {playerMessage}
                </p>
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
                }}
              />
            </>
          )}

          {/* 방송 종료 상태 */}
          {liveStatus === 'OFFLINE' && (
            <div
              style={{
                width: '100%',
                minHeight: '400px',
                backgroundColor: '#111',
                borderRadius: '12px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#aaa',
              }}
            >
              현재 진행 중인 라이브 방송이 없습니다.
            </div>
          )}

          {/* SRS 서버 장애 */}
          {liveStatus === 'SRS_DOWN' && (
            <div
              style={{
                width: '100%',
                minHeight: '400px',
                backgroundColor: '#111',
                borderRadius: '12px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#aaa',
              }}
            >
              스트리밍 서버에 연결할 수 없습니다.
            </div>
          )}
        </div>
      </div>
    </main>
  );
}