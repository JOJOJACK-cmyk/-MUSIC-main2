import React, { useEffect, useRef } from 'react';
import { usePlayer } from '../context/PlayerContext';

export default function YouTubePlayer() {
  const containerRef = useRef(null);
  const playerRef = useRef(null);

  const {
    currentTrack,
    registerPlayer,
    handlePlayerStateChange,
  } = usePlayer();

  // 미리듣기 60초 제한은 PlayerContext 한 곳에서 통제한다. (여기서 중복 처리하지 않음)

  const stateHandlerRef = useRef(handlePlayerStateChange);
  useEffect(() => {
    stateHandlerRef.current = handlePlayerStateChange;
  }, [handlePlayerStateChange]);

  // 1. YouTube IFrame API 스크립트 로드 및 Player 객체 생성
  useEffect(() => {
    let isCancelled = false;

    if (!currentTrack?.youtubeVideoId) return;

    const createPlayer = () => {
      if (isCancelled || playerRef.current || !containerRef.current) return;

      playerRef.current = new window.YT.Player(containerRef.current, {
        height: '100%',
        width: '100%',
        videoId: currentTrack.youtubeVideoId,
        playerVars: {
          autoplay: 1,
          playsinline: 1,
          controls: 1,
          modestbranding: 1,
          origin: window.location.origin,
        },
        events: {
          onReady: (event) => {
            registerPlayer(event.target);
            if (currentTrack?.youtubeVideoId) {
              event.target.playVideo();
            }
          },
          onStateChange: (event) => {
            if (stateHandlerRef.current) {
              stateHandlerRef.current(event);
            }
          },
          onError: (e) => {
            console.warn('YouTube Player 에러 발생 (코드:', e.data, ')');
          }
        },
      });
    };

    if (window.YT && window.YT.Player) {
      createPlayer();
    } else {
      if (!document.querySelector('script[src="https://www.youtube.com/iframe_api"]')) {
        const tag = document.createElement('script');
        tag.src = 'https://www.youtube.com/iframe_api';
        document.body.appendChild(tag);
      }

      const prevOnReady = window.onYouTubeIframeAPIReady;
      window.onYouTubeIframeAPIReady = () => {
        if (typeof prevOnReady === 'function') prevOnReady();
        createPlayer();
      };
    }

    return () => {
      isCancelled = true;
      try {
        playerRef.current?.destroy?.();
      } catch (e) {}
      playerRef.current = null;
      registerPlayer(null);
    };
  }, [registerPlayer, currentTrack?.youtubeVideoId]);

  if (!currentTrack || !currentTrack.youtubeVideoId) {
    return null;
  }

  return (
    <div className="youtube-player-floating">
      <div className="youtube-player-title" style={{ display: 'flex', alignItems: 'center', padding: '4px 8px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', overflow: 'hidden', width: '100%' }}>
          <span style={{ fontSize: '11px', opacity: 0.7, flexShrink: 0 }}>NOW PLAYING</span>
          <strong style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', fontSize: '12px' }}>
            {currentTrack?.title || '재생 중인 곡 없음'}
          </strong>
        </div>
      </div>

      <div
        ref={containerRef}
        style={{
          width: '100%',
          height: '200px'
        }}
      />
    </div>
  );
}