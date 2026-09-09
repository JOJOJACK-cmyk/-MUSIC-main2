import React, { useEffect, useRef } from 'react';
import { usePlayer } from '../context/PlayerContext';

/**
 * 백그라운드 탭에서도 곡이 자동으로 넘어가도록,
 * YT.Player 를 곡마다 새로 만들지 않고 **하나만 유지**한 채 loadVideoById 로 교체한다.
 * (플레이어를 매번 destroy/create 하면 백그라운드 탭에서 iframe 핸드셰이크가 지연돼
 *  탭으로 돌아와야 다음 곡이 재생된다.)
 */
export default function YouTubePlayer() {
  const containerRef = useRef(null);
  const playerRef = useRef(null);
  const loadedVideoRef = useRef(null);

  const {
    currentTrack,
    isPlaying,
    registerPlayer,
    handlePlayerStateChange,
    handlePlayerError,
    consumeRestoreSeek,
  } = usePlayer();

  const stateHandlerRef = useRef(handlePlayerStateChange);
  useEffect(() => { stateHandlerRef.current = handlePlayerStateChange; }, [handlePlayerStateChange]);
  const errorHandlerRef = useRef(handlePlayerError);
  useEffect(() => { errorHandlerRef.current = handlePlayerError; }, [handlePlayerError]);
  const consumeRef = useRef(consumeRestoreSeek);
  useEffect(() => { consumeRef.current = consumeRestoreSeek; }, [consumeRestoreSeek]);

  const videoId = currentTrack?.youtubeVideoId || null;
  const currentTrackRef = useRef(currentTrack);
  currentTrackRef.current = currentTrack;

  // 1) 유튜브 API 로드 + 플레이어를 1회만 생성 (언마운트 때만 파괴)
  useEffect(() => {
    let cancelled = false;

    if (!document.querySelector('script[src="https://www.youtube.com/iframe_api"]')) {
      const tag = document.createElement('script');
      tag.src = 'https://www.youtube.com/iframe_api';
      document.body.appendChild(tag);
    }

    const create = () => {
      if (cancelled || playerRef.current) return;
      if (!window.YT || !window.YT.Player || !containerRef.current) return;

      const vid = currentTrackRef.current?.youtubeVideoId;
      if (!vid) return;
      const startAt = consumeRef.current?.(vid) || 0;
      const playerVars = {
        autoplay: startAt > 0 ? 0 : 1,
        playsinline: 1, controls: 1, modestbranding: 1,
        origin: window.location.origin,
      };
      if (startAt > 0) playerVars.start = startAt;

      loadedVideoRef.current = vid;
      playerRef.current = new window.YT.Player(containerRef.current, {
        height: '100%', width: '100%', videoId: vid, playerVars,
        events: {
          onReady: (e) => {
            registerPlayer(e.target);
            if (startAt <= 0) e.target.playVideo();
          },
          onStateChange: (e) => stateHandlerRef.current?.(e),
          onError: (e) => errorHandlerRef.current?.(e.data),
        },
      });
    };

    create();
    const poll = setInterval(() => {
      if (playerRef.current) { clearInterval(poll); return; }
      create();
    }, 600);

    return () => {
      cancelled = true;
      clearInterval(poll);
      try { playerRef.current?.destroy?.(); } catch (e) {}
      playerRef.current = null;
      loadedVideoRef.current = null;
      registerPlayer(null);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [registerPlayer]);

  // 2) 곡이 바뀌면 기존 플레이어에 새 영상만 물린다 (재생성 X).
  //    곡이 비워지면(미리듣기 잠금 등) 플레이어를 파기한다.
  useEffect(() => {
    if (!videoId) {
      if (playerRef.current) {
        try { playerRef.current.destroy?.(); } catch (e) {}
        playerRef.current = null;
        loadedVideoRef.current = null;
        registerPlayer(null);
      }
      return;
    }
    const player = playerRef.current;
    if (!player || loadedVideoRef.current === videoId) return;
    loadedVideoRef.current = videoId;
    try {
      const startAt = consumeRef.current?.(videoId) || 0;
      if (startAt > 0) {
        player.cueVideoById?.({ videoId, startSeconds: startAt });
      } else {
        player.loadVideoById?.(videoId);
        player.playVideo?.();
      }
    } catch (e) {}
  }, [videoId, registerPlayer]);

  if (!currentTrack || !videoId) return null;

  const hiddenStyle = isPlaying ? null : { opacity: 0, pointerEvents: 'none' };

  return (
    <div className="youtube-player-floating" style={hiddenStyle}>
      <div className="youtube-player-title" style={{ display: 'flex', alignItems: 'center', padding: '4px 8px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', overflow: 'hidden', width: '100%' }}>
          <span style={{ fontSize: '11px', opacity: 0.7, flexShrink: 0 }}>NOW PLAYING</span>
          <strong style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', fontSize: '12px' }}>
            {currentTrack?.title || '재생 중인 곡 없음'}
          </strong>
        </div>
      </div>
      <div ref={containerRef} style={{ width: '100%', height: '200px' }} />
    </div>
  );
}
