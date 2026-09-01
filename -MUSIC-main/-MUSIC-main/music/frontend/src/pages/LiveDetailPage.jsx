import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Hls from 'hls.js';

import Header from '../components/Header';
import LiveVotingRoom from '../components/LiveVotingRoom';
import LiveChat from '../components/LiveChat';
import { useAuth } from '../context/AuthContext';

export default function LiveDetailPage() {
  const { broadcastId } = useParams();
  const navigate = useNavigate();
  const videoRef = useRef(null);

  const { user } = useAuth();

  const [broadcast, setBroadcast] = useState(null);
  const [loading, setLoading] = useState(true);
  const [playerMessage, setPlayerMessage] = useState('');

  const liveApiUrl =
    'http://localhost:8080/api/broadcast/live';

  // 현재 로그인 사용자 이름
  const currentUserName =
    user?.nickname ||
    user?.name ||
    user?.email?.split('@')[0] ||
    '';

  // 현재 로그인 사용자가 이 방송의 방송자인지 확인
  const isBroadcaster =
    !!broadcast &&
    !!currentUserName &&
    broadcast.broadcaster === currentUserName;

  // 1. 현재 방송 정보 조회
  useEffect(() => {
    const fetchBroadcast = async () => {
      try {
        const response = await fetch(liveApiUrl, {
          credentials: 'include',
        });

        if (!response.ok) {
          throw new Error('방송 정보 조회 실패');
        }

        const data = await response.json();

        const found = data.find(
          (item) =>
            String(item.id) === String(broadcastId)
        );

        setBroadcast(found || null);
      } catch (error) {
        console.error('방송 조회 실패:', error);
        setBroadcast(null);
      } finally {
        setLoading(false);
      }
    };

    fetchBroadcast();

    // 시청자 수 / 방송 종료 상태 갱신
    const interval = setInterval(
      fetchBroadcast,
      3000
    );

    return () => {
      clearInterval(interval);
    };
  }, [broadcastId]);

  // 2. HLS 영상 연결
  useEffect(() => {
    const video = videoRef.current;

    if (!video || !broadcast?.hlsUrl) {
      return;
    }

    const hlsUrl = broadcast.hlsUrl;

    setPlayerMessage(
      '라이브 스트림 연결 중...'
    );

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
          console.error('HLS 오류:', data);

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
  }, [broadcast?.hlsUrl]);

  // 3. Redis 시청자 heartbeat
  useEffect(() => {
    if (!broadcast?.id) {
      return;
    }

    const sendHeartbeat = async () => {
      try {
        await fetch(
          `http://localhost:8080/api/broadcast/${broadcast.id}/viewers/heartbeat`,
          {
            method: 'POST',
            credentials: 'include',
          }
        );
      } catch (error) {
        console.error(
          '시청자 heartbeat 전송 실패:',
          error
        );
      }
    };

    sendHeartbeat();

    const interval = setInterval(
      sendHeartbeat,
      5000
    );

    return () => {
      clearInterval(interval);
    };
  }, [broadcast?.id]);

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
        {/* 목록으로 돌아가기 */}
        <button
          onClick={() => navigate('/live')}
          style={{
            marginBottom: '18px',
            background: 'none',
            border: 'none',
            color: 'var(--text-sub)',
            cursor: 'pointer',
            fontSize: '14px',
          }}
        >
          ← 라이브 목록
        </button>

        {/* 로딩 */}
        {loading && (
          <div
            style={{
              padding: '80px',
              textAlign: 'center',
            }}
          >
            방송 정보를 불러오는 중...
          </div>
        )}

        {/* 존재하지 않는 방송 */}
        {!loading && !broadcast && (
          <div
            style={{
              minHeight: '450px',
              backgroundColor: '#111',
              borderRadius: '14px',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '16px',
              color: '#aaa',
            }}
          >
            <div>
              현재 종료되었거나 존재하지 않는
              방송입니다.
            </div>

            <button
              onClick={() =>
                navigate('/live')
              }
              style={{
                padding: '10px 16px',
                cursor: 'pointer',
              }}
            >
              라이브 목록으로
            </button>
          </div>
        )}

        {/* 방송 존재 */}
        {!loading && broadcast && (
          <>
            {/* 실제 라이브 영상 */}
            <div
              style={{
                position: 'relative',
                backgroundColor: '#000',
                borderRadius: '14px',
                overflow: 'hidden',
                maxWidth: '1100px',
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
                }}
              />

              {/* LIVE 표시 */}
              <div
                style={{
                  position: 'absolute',
                  top: '14px',
                  left: '14px',
                  backgroundColor: '#e91916',
                  color: '#fff',
                  padding: '5px 9px',
                  borderRadius: '5px',
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
                  top: '14px',
                  right: '14px',
                  backgroundColor:
                    'rgba(0,0,0,0.75)',
                  color: '#fff',
                  padding: '5px 9px',
                  borderRadius: '5px',
                  fontSize: '12px',
                }}
              >
                👥 {broadcast.viewerCount ?? 0}명
              </div>
            </div>

            {/* 플레이어 상태 */}
            {playerMessage && (
              <div
                style={{
                  marginTop: '10px',
                  color: 'var(--text-sub)',
                }}
              >
                {playerMessage}
              </div>
            )}

            {/* 방송 정보 */}
            <div
              style={{
                maxWidth: '1100px',
                padding: '18px 4px',
                borderBottom:
                  '1px solid #333',
              }}
            >
              <h2
                style={{
                  marginBottom: '10px',
                }}
              >
                {broadcast.title}
              </h2>

              <div
                style={{
                  color: 'var(--text-sub)',
                }}
              >
                방송자: {broadcast.broadcaster}
                {' · '}
                👥 {broadcast.viewerCount ?? 0}명
                시청 중
              </div>
            </div>

            {/* 신청곡 / 채팅 */}
            <div
              style={{
                maxWidth: '1100px',
                marginTop: '24px',
                display: 'grid',
                gridTemplateColumns:
                  '2fr 1fr',
                gap: '20px',
              }}
            >
              {/* 신청곡 / 투표 */}
              <div
                style={{
                  minHeight: '250px',
                  backgroundColor: '#181818',
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <LiveVotingRoom
                  broadcastId={broadcastId}
                  isBroadcaster={
                    isBroadcaster
                  }
                />
              </div>

              {/* 실시간 채팅 */}
              <div
                style={{
                  minHeight: '250px',
                  backgroundColor: '#181818',
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <LiveChat
                  broadcastId={broadcastId}
                />
              </div>
            </div>
          </>
        )}
      </div>
    </main>
  );
}