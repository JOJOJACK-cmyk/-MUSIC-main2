import React, { useEffect, useRef } from 'react';
import { usePlayer } from '../context/PlayerContext';

export default function YouTubePlayer() {
  const containerRef = useRef(null);
  const playerRef = useRef(null);

  const {
    currentTrack,
    registerPlayer,
    handlePlayerStateChange
  } = usePlayer();

  const stateHandlerRef = useRef(handlePlayerStateChange);
  useEffect(() => {
    stateHandlerRef.current = handlePlayerStateChange;
  }, [handlePlayerStateChange]);

  // 1. YouTube IFrame API 스크립트 로드 및 Player 객체 생성 (1회 초기화)
  useEffect(() => {
    let isCancelled = false;

    const createPlayer = () => {
      if (isCancelled || playerRef.current || !containerRef.current) return;

      playerRef.current = new window.YT.Player(containerRef.current, {
        height: '100%',
        width: '100%',
        videoId: currentTrack?.youtubeVideoId || '',
        playerVars: {
          autoplay: 1,
          playsinline: 1,
          controls: 0,
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
      if (playerRef.current && typeof playerRef.current.destroy === 'function') {
        playerRef.current.destroy();
      }
      playerRef.current = null;
      registerPlayer(null);
    };
  }, [registerPlayer]);

  // 2. 트랙이 바뀌었을 때 영상 로드 및 재생
  useEffect(() => {
    const player = playerRef.current;
    if (player && currentTrack?.youtubeVideoId && typeof player.loadVideoById === 'function') {
      player.loadVideoById(currentTrack.youtubeVideoId);
      player.playVideo();
    }
  }, [currentTrack?.youtubeVideoId]);

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 0,
        right: 0,
        width: '200px',
        height: '120px',
        opacity: 0,
        pointerEvents: 'none',
        zIndex: -1,
      }}
    >
      <div ref={containerRef} />
    </div>
  );
}