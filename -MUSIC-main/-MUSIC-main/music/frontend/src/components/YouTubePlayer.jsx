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
          controls: 1, // 유튜브 자체 컨트롤러(화질 설정, 전체화면 버튼 포함) 활성화
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

  // 3. 💡 비로그인 사용자 1분(60초) 제한 감지 로직 수정 (알림 블로킹 우회 적용)
  useEffect(() => {
    // 로그인 상태이거나 플레이어가 없으면 감지할 필요 없음
    if (!isLimitedUser) return;

    const interval = setInterval(() => {
      const player = playerRef.current;
      // 플레이어가 존재하고, 현재 재생 중(YT.PlayerState.PLAYING은 보통 1)인지 확인
      if (player && typeof player.getPlayerState === 'function' && player.getPlayerState() === window.YT?.PlayerState?.PLAYING) {
        const currentTime = player.getCurrentTime();

        if (currentTime >= 60) {
          // 💡 1. 즉시 음악 정지 및 처음으로 이동하여 바로 멈추게 함
          player.pauseVideo();
          player.seekTo(0);

          // 💡 2. 브라우저 스레드가 잠기지 않도록 setTimeout으로 alert 지연 실행
          setTimeout(() => {
            alert('비로그인 사용자는 1분까지만 미리 듣기할 수 있습니다. 전체 곡을 감상하려면 로그인해주세요!');
          }, 100);
        }
      }
    }, 500); // 0.5초마다 현재 재생 시간 체크

    return () => clearInterval(interval);
  }, [isLimitedUser]);

  return (
    <div
      className="youtube-player-floating"
      style={{ display: currentTrack ? 'block' : 'none' }} // 💡 곡이 없으면 display: none으로 숨기고, 있으면 보여줌
    >
      {/* 상단 타이틀 바에는 곡 제목만 깔끔하게 표시합니다 */}
      <div className="youtube-player-title" style={{ display: 'flex', alignItems: 'center', padding: '4px 8px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', overflow: 'hidden', width: '100%' }}>
          <span style={{ fontSize: '11px', opacity: 0.7, flexShrink: 0 }}>NOW PLAYING</span>
          <strong style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', fontSize: '12px' }}>
            {currentTrack?.title || '재생 중인 곡 없음'}
          </strong>
        </div>
      </div>

      {/* 유튜브 플레이어 영역 */}
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