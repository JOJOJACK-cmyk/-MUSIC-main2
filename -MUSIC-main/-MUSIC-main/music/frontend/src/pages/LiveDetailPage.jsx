import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Hls from 'hls.js';

import Header from '../components/Header';
import LivePoll from '../components/LivePoll';
import LiveChat from '../components/LiveChat';
import LiveVideoControls from '../components/LiveVideoControls';
import FollowButton from '../components/FollowButton';
import NoteDonationModal from '../components/NoteDonationModal';
import useIsMobile from '../hooks/useIsMobile';
import { useAuth } from '../context/AuthContext';
import { useLiveView } from '../context/LiveViewContext';

// 시청자 수 집계용 탭 단위 랜덤 ID (세션 쿠키 대신 사용 — 비로그인 시청자도 1명으로 정확히 집계)
let fallbackViewerId = null;
const getViewerId = () => {
  try {
    let id = sessionStorage.getItem('liveViewerId');
    if (!id) {
      id = (window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`)
        .replace(/[^A-Za-z0-9-]/g, '');
      sessionStorage.setItem('liveViewerId', id);
    }
    return id;
  } catch {
    // sessionStorage 차단 환경: 페이지 수명 동안 같은 ID 유지
    if (!fallbackViewerId) fallbackViewerId = 'anon-' + Math.random().toString(36).slice(2, 12);
    return fallbackViewerId;
  }
};

export default function LiveDetailPage() {
  const [showNotes, setShowNotes] = useState(false); // 음표 보내기 모달
  const isMobile = useIsMobile();
  const [mobileTab, setMobileTab] = useState('chat'); // 모바일: 채팅 | 투표
  const { broadcastId } = useParams();
  const navigate = useNavigate();
  const videoRef = useRef(null);
  const videoWrapRef = useRef(null);
  const videoColRef = useRef(null);
  const [chatHeight, setChatHeight] = useState(null);

  const { user } = useAuth();
  const { setActiveBroadcast } = useLiveView() || {};

  const [broadcast, setBroadcast] = useState(null);
  const [loading, setLoading] = useState(true);
  const [playerMessage, setPlayerMessage] = useState('');

  const liveApiUrl =
    '/api/broadcast/live';

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
  // 음표는 로그인한 시청자만 (방송자 본인 제외 — 서버도 다시 검사)
  const canSendNotes = !!user && !!broadcast && !isBroadcaster;

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

        // 다른 페이지로 이동해도 화면 한켠에 작게 이어 보여주는 미니 플레이어용 상태.
        // 방송이 끝나(목록에서 사라지) 있으면 미니 플레이어도 자동으로 닫는다.
        if (found?.hlsUrl) {
          setActiveBroadcast?.({
            id: found.id,
            hlsUrl: found.hlsUrl,
            title: found.title,
            broadcasterNickname: found.broadcaster,
          });
        } else {
          setActiveBroadcast?.((prev) => (prev?.id === Number(broadcastId) || String(prev?.id) === String(broadcastId) ? null : prev));
        }
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

  // 사용자가 컨트롤바에서 "직접" 일시정지했는지 (그 경우엔 자동 재개 안 함)
  const manualPauseRef = useRef(false);

  // 2. HLS 영상 연결 (화면 이동 후 돌아와도 실시간 지점에서 자동 재개)
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !broadcast?.hlsUrl) return;

    const hlsUrl = broadcast.hlsUrl;
    setPlayerMessage('라이브 스트림 연결 중...');

    // 실시간 끝 지점으로 붙이고 재생
    const jumpToLiveAndPlay = () => {
      setPlayerMessage('');
      try {
        const s = video.seekable;
        if (s && s.length) {
          const end = s.end(s.length - 1);
          if (end - video.currentTime > 6) video.currentTime = Math.max(0, end - 1);
        }
      } catch (_) {}
      if (!manualPauseRef.current) video.play().catch(() => {});
    };

    let hls = null;
    let cleanupSafari = null;

    if (Hls.isSupported()) {
      hls = new Hls({ liveSyncDurationCount: 3, lowLatencyMode: true });
      hls.loadSource(hlsUrl);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, jumpToLiveAndPlay);
      hls.on(Hls.Events.ERROR, (evt, data) => {
        if (!data.fatal) return;
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
          setPlayerMessage('스트림 재연결 중…');
          hls.startLoad();
        } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          hls.recoverMediaError();
        } else {
          setPlayerMessage('라이브 영상을 불러오는 중 문제가 발생했습니다.');
        }
      });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      const onMeta = () => jumpToLiveAndPlay();
      video.src = hlsUrl;
      video.addEventListener('loadedmetadata', onMeta);
      cleanupSafari = () => {
        video.removeEventListener('loadedmetadata', onMeta);
        video.pause();
        video.removeAttribute('src');
        video.load();
      };
    } else {
      setPlayerMessage('이 브라우저에서는 HLS 재생을 지원하지 않습니다.');
    }

    // 탭 복귀 시 실시간 지점으로 재동기화 (직접 멈춘 게 아니면)
    const onVisible = () => {
      if (document.visibilityState === 'visible') jumpToLiveAndPlay();
    };
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      document.removeEventListener('visibilitychange', onVisible);
      if (hls) hls.destroy();
      if (cleanupSafari) cleanupSafari();
    };
  }, [broadcast?.hlsUrl]);

  // 3. Redis 시청자 heartbeat
  useEffect(() => {
    if (!broadcast?.id) {
      return;
    }

    const sendHeartbeat = async () => {
      try {
        await fetch(
          `/api/broadcast/${broadcast.id}/viewers/heartbeat?viewerId=${getViewerId()}`,
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

  // 4. 채팅 패널 높이를 영상 칼럼 높이에 맞춤 (CSS Grid의 auto-row는 내용이
  // 늘어나면 같이 늘어나므로, 채팅이 쌓여도 페이지가 밀리지 않게 실측해서 고정한다)
  useEffect(() => {
    if (loading || !broadcast) return;
    const el = videoColRef.current;
    if (!el) return;

    const mq = window.matchMedia('(max-width: 1040px)');

    // 영상이 작아도(창이 좁거나 투표가 열려도) 채팅이 읽을 만한 높이는 유지한다
    const MIN_CHAT_HEIGHT = 560;
    const update = () => {
      setChatHeight(mq.matches ? null : Math.max(el.getBoundingClientRect().height, MIN_CHAT_HEIGHT));
    };

    update();

    const ro = new ResizeObserver(update);
    ro.observe(el);
    mq.addEventListener('change', update);

    return () => {
      ro.disconnect();
      mq.removeEventListener('change', update);
    };
  }, [loading, broadcast?.id]);

  // ── 모바일 전용 화면: 영상(상단 고정) · 방송 정보 · [채팅 | 투표] 탭 ──
  //    채팅/투표는 탭을 바꿔도 연결이 끊기지 않게 둘 다 띄워 두고 보이기만 바꾼다.
  if (isMobile) {
    // 방송자가 신청곡을 끄면 투표 탭이 사라지므로 채팅으로 돌린다 (빈 패널 방지)
    const tab = broadcast?.songRequestEnabled ? mobileTab : 'chat';
    const video = (
      <div className="mld-video">
        <div className="ld-video" ref={videoWrapRef}>
          <video
            ref={videoRef}
            autoPlay
            muted
            playsInline
            style={{ width: '100%', height: '100%', display: 'block', objectFit: 'contain', background: '#000' }}
          />
          {playerMessage && <div className="ld-video-msg">{playerMessage}</div>}
        </div>
        {broadcast && (
          <LiveVideoControls
            videoRef={videoRef}
            wrapRef={videoWrapRef}
            startedAt={broadcast.startedAt}
            viewerCount={broadcast.viewerCount}
            manualPauseRef={manualPauseRef}
          />
        )}
      </div>
    );
    return (
      <div className="mld">
        {loading ? (
          <div className="m-empty"><i className="fa-solid fa-tower-broadcast fa-fade" />방송 정보를 불러오는 중…</div>
        ) : !broadcast ? (
          <div className="m-empty">
            <i className="fa-solid fa-tower-broadcast" />종료되었거나 없는 방송이에요
            <button className="m-btn ghost small" onClick={() => navigate('/live')}>라이브 목록</button>
          </div>
        ) : (
          <>
            {video}
            <div className="mld-info">
              <span className="m-live-av">{(broadcast.broadcaster || '?').charAt(0)}</span>
              <div className="mld-info-text">
                <strong>{broadcast.title}</strong>
                <small>{broadcast.broadcaster} · 👥 {broadcast.viewerCount ?? 0}명 시청 중</small>
              </div>
              <FollowButton channelUserId={broadcast.broadcasterId} size="sm" />
            </div>
            {canSendNotes && (
              <button className="note-open-btn mld-note" onClick={() => setShowNotes(true)}>
                <span className="note-open-icon">♪</span> 음표 보내기
              </button>
            )}
            <div className="mld-tabs">
              <button className={tab === 'chat' ? 'active' : ''} onClick={() => setMobileTab('chat')}>
                <i className="fa-solid fa-comment-dots" /> 채팅
              </button>
              {broadcast.songRequestEnabled && (
                <button className={tab === 'poll' ? 'active' : ''} onClick={() => setMobileTab('poll')}>
                  <i className="fa-solid fa-square-poll-vertical" /> 신청곡 투표
                </button>
              )}
            </div>
            <div className="mld-panel">
              <div className="mld-pane" style={{ display: tab === 'chat' ? 'flex' : 'none' }}>
                <LiveChat
                  broadcastId={broadcastId}
                  isBroadcaster={isBroadcaster}
                  onSendNotes={canSendNotes ? () => setShowNotes(true) : undefined}
                />
              </div>
              {broadcast.songRequestEnabled && (
                <div className="mld-pane poll" style={{ display: tab === 'poll' ? 'block' : 'none' }}>
                  <LivePoll broadcastId={broadcastId} isBroadcaster={isBroadcaster} />
                </div>
              )}
            </div>
          </>
        )}
        {showNotes && broadcast && (
          <NoteDonationModal
            broadcastId={broadcast.id}
            broadcasterName={broadcast.broadcaster}
            onClose={() => setShowNotes(false)}
          />
        )}
      </div>
    );
  }

  return (
    <>
      <Header
        searchTerm=""
        setSearchTerm={() => {}}
      />

      <div
        className="content-section"
        style={{
          // width 가 없으면 margin:auto 때문에 내용 폭으로 줄어들어(약 760px) 영상·채팅이 같이 작아진다
          width: '100%',
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
            {/* 상단: 영상+컨트롤바(좌) + 채팅(우) — 채팅 높이는 왼쪽 칼럼에 맞춤 */}
            <div className="ld-stage">
              <div className="ld-video-col" ref={videoColRef}>
                <div className="ld-video" ref={videoWrapRef}>
                  <video
                    ref={videoRef}
                    autoPlay
                    muted
                    playsInline
                    style={{ width: '100%', height: '100%', display: 'block', objectFit: 'contain', background: '#000' }}
                  />
                  {playerMessage && <div className="ld-video-msg">{playerMessage}</div>}
                </div>
                <LiveVideoControls
                  videoRef={videoRef}
                  wrapRef={videoWrapRef}
                  startedAt={broadcast.startedAt}
                  viewerCount={broadcast.viewerCount}
                  manualPauseRef={manualPauseRef}
                />
              </div>

              {/* 오른쪽: (투표) + 채팅 */}
              <aside
                className="ld-chat"
                style={chatHeight ? { height: `${chatHeight}px` } : undefined}
              >
                {broadcast.songRequestEnabled && (
                  <LivePoll broadcastId={broadcastId} isBroadcaster={isBroadcaster} />
                )}
                <div className="ld-chat-body">
                  <LiveChat
                    broadcastId={broadcastId}
                    isBroadcaster={isBroadcaster}
                    onSendNotes={canSendNotes ? () => setShowNotes(true) : undefined}
                  />
                </div>
              </aside>
            </div>

            {/* 방송 정보 (영상 아래, 전체 폭) */}
            <div style={{ padding: '18px 4px', borderBottom: '1px solid #333' }}>
              <h2 style={{ marginBottom: '10px' }}>{broadcast.title}</h2>
              <div
                style={{
                  color: 'var(--text-sub)', display: 'flex', alignItems: 'center',
                  gap: '12px', flexWrap: 'wrap',
                }}
              >
                <span>방송자: {broadcast.broadcaster}</span>
                <FollowButton channelUserId={broadcast.broadcasterId} size="sm" />
                {canSendNotes && (
                  <button className="note-open-btn" onClick={() => setShowNotes(true)}>
                    <span className="note-open-icon">♪</span> 음표 보내기
                  </button>
                )}
                <span>· 👥 {broadcast.viewerCount ?? 0}명 시청 중</span>
              </div>
            </div>
          </>
        )}
      </div>

      {showNotes && broadcast && (
        <NoteDonationModal
          broadcastId={broadcast.id}
          broadcasterName={broadcast.broadcaster}
          onClose={() => setShowNotes(false)}
        />
      )}
    </>
  );
}