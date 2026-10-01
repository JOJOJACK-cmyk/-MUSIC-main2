import React, { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import Hls from 'hls.js';
import { useLiveView } from '../context/LiveViewContext';

/**
 * 라이브 상세 페이지를 벗어나도 화면 한 켠에 작게 떠서 이어 보여주는 라이브 방송 미니 플레이어.
 * 유튜브 음악의 "NOW PLAYING" 플로팅 박스(우측)와 겹치지 않도록 좌측에 띄운다.
 * 다시 그 방송 페이지로 돌아가면(같은 broadcastId) 숨겨서 큰 화면 하나만 재생되게 한다.
 */
export default function LiveMiniPlayer() {
  const { activeBroadcast, setActiveBroadcast } = useLiveView() || {};
  const location = useLocation();
  const navigate = useNavigate();
  const videoRef = useRef(null);
  const [muted, setMuted] = useState(true);

  const onLiveDetailPage =
    activeBroadcast && location.pathname === `/live/${activeBroadcast.id}`;

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !activeBroadcast?.hlsUrl || onLiveDetailPage) return;

    let hls = null;
    let fatalErrorCount = 0;

    if (Hls.isSupported()) {
      hls = new Hls({ liveSyncDurationCount: 3, lowLatencyMode: true });
      hls.loadSource(activeBroadcast.hlsUrl);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => video.play().catch(() => {}));
      hls.on(Hls.Events.ERROR, (_evt, data) => {
        if (!data.fatal) return;
        fatalErrorCount += 1;
        // 방송이 실제로 끝난 경우(계속 복구 실패) 미니 플레이어를 자동으로 닫는다.
        if (fatalErrorCount > 3) { setActiveBroadcast?.(null); return; }
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) hls.startLoad();
        else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) hls.recoverMediaError();
      });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = activeBroadcast.hlsUrl;
      video.play().catch(() => {});
    }

    return () => { if (hls) hls.destroy(); };
  }, [activeBroadcast?.hlsUrl, onLiveDetailPage, setActiveBroadcast]);

  if (!activeBroadcast || onLiveDetailPage) return null;

  return (
    <div className="live-mini-player">
      <div className="live-mini-player-title">
        <span className="live-mini-dot" />
        <strong>{activeBroadcast.title || '라이브 방송'}</strong>
        <button
          className="live-mini-btn"
          title={muted ? '소리 켜기' : '소리 끄기'}
          onClick={() => {
            const v = videoRef.current;
            if (v) v.muted = !v.muted;
            setMuted((m) => !m);
          }}
        >
          <i className={`fa-solid ${muted ? 'fa-volume-xmark' : 'fa-volume-high'}`} />
        </button>
        <button className="live-mini-btn" title="닫기" onClick={() => setActiveBroadcast?.(null)}>
          <i className="fa-solid fa-xmark" />
        </button>
      </div>
      <div className="live-mini-player-video" onClick={() => navigate(`/live/${activeBroadcast.id}`)}>
        <video ref={videoRef} muted={muted} playsInline autoPlay />
      </div>
    </div>
  );
}
