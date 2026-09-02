import React, { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePlayer } from '../context/PlayerContext';
import { useAuth } from '../context/AuthContext';

export default function YouTubePlayer() {
  const containerRef = useRef(null);
  const playerRef = useRef(null);
  const navigate = useNavigate();

  const {
    currentTrack,
    registerPlayer,
    handlePlayerStateChange,
    playTrack
  } = usePlayer();

  const { user } = useAuth();

  const isPremium = user?.isPremium || user?.subscribed || user?.role === 'PREMIUM' || user?.membership === 'PREMIUM';
  const isLimitedUser = !user || !isPremium;

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

  // 2. 💡 [핵심 해결] 60초가 되는 순간 곡을 강제로 비우고 즉시 결제 페이지로 점프
  useEffect(() => {
    if (!isLimitedUser || !currentTrack) return;

    const timer = setTimeout(() => {
      // 먼저 전역 곡 데이터를 비워서 플레이어 바와 유튜브 IFrame을 동시에 폭파
      if (typeof playTrack === 'function') {
        try {
          playTrack(null);
        } catch (e) {}
      }

      try {
        if (playerRef.current && typeof playerRef.current.destroy === 'function') {
          playerRef.current.destroy();
        }
      } catch (e) {}
      playerRef.current = null;
      registerPlayer(null);

      // 브라우저 블로킹을 피하기 위해 alert를 띄운 후 곧바로 라우팅
      alert('무료 회원(또는 미결제 회원)은 1분까지만 미리 듣기할 수 있습니다. 전체 곡을 감상하려면 프리미엄 이용권을 구매해주세요!');
      navigate('/payment');

    }, 60000); // 정확히 60초

    return () => clearTimeout(timer);
  }, [isLimitedUser, currentTrack, navigate, registerPlayer, playTrack]);

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