import React, { useEffect, useRef } from 'react';
import { usePlayer } from '../context/PlayerContext';
import { useAuth } from '../context/AuthContext'; // 1. AuthContext 임포트

export default function YouTubePlayer() {
  const containerRef = useRef(null);
  const playerRef = useRef(null);

  const {
    currentTrack,
    registerPlayer,
    handlePlayerStateChange
  } = usePlayer();

  const { user } = useAuth(); // 2. 로그인 유저 정보 가져오기 (없으면 비로그인)
  const isLimitedUser = !user; // 비로그인 여부 판단

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

  // 3. 💡 비로그인 사용자 1분(60초) 제한 감지 로직 추가
  useEffect(() => {
    // 로그인 상태이거나 플레이어가 없으면 감지할 필요 없음
    if (!isLimitedUser) return;

    const interval = setInterval(() => {
      const player = playerRef.current;
      // 플레이어가 존재하고, 현재 재생 중(YT.PlayerState.PLAYING은 보통 1)인지 확인
      if (player && typeof player.getPlayerState === 'function' && player.getPlayerState() === window.YT?.PlayerState?.PLAYING) {
        const currentTime = player.getCurrentTime();

        if (currentTime >= 60) {
          player.pauseVideo(); // 60초 도달 시 일시 정지
          player.seekTo(0);    // 처음으로 되돌리기 (선택사항)

          alert('비로그인 사용자는 1분까지만 미리 듣기할 수 있습니다. 전체 곡을 감상하려면 로그인해주세요!');

          // 필요시 로그인 페이지로 이동하는 로직 추가 가능
          // window.location.href = '/login';
        }
      }
    }, 500); // 0.5초마다 현재 재생 시간 체크

    return () => clearInterval(interval);
  }, [isLimitedUser]);

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