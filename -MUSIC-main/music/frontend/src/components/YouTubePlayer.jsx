import React, {
  useEffect,
  useRef
} from 'react';

import { usePlayer }
  from '../context/PlayerContext';


export default function YouTubePlayer({
  videoId
}) {

  // YouTube 플레이어가 들어갈 div
  const containerRef =
    useRef(null);


  // 실제 YT.Player 객체
  const playerRef =
    useRef(null);


  // 최신 videoId 저장
  const videoIdRef =
    useRef(videoId);

  videoIdRef.current =
    videoId;


  const {
    registerPlayer,
    handlePlayerStateChange
  } = usePlayer();


  // 최신 상태 이벤트 함수를 저장
  const stateHandlerRef =
    useRef(
      handlePlayerStateChange
    );


  useEffect(() => {

    stateHandlerRef.current =
      handlePlayerStateChange;

  }, [
    handlePlayerStateChange
  ]);


  useEffect(() => {

    let cancelled = false;


    // 실제 YouTube Player 생성
    const createPlayer = () => {

      if (
        cancelled ||
        playerRef.current ||
        !containerRef.current
      ) {

        return;
      }


      playerRef.current =
        new window.YT.Player(
          containerRef.current,
          {

            width: '100%',
            height: '100%',

            videoId:
              videoIdRef.current,


            playerVars: {

              autoplay: 1,

              playsinline: 1,

              origin:
                window.location.origin,
            },


            events: {

              // 플레이어 준비 완료
              onReady: (event) => {

                registerPlayer(
                  event.target
                );


                event.target
                  .playVideo();
              },


              // 재생 / 정지 / 종료 상태
              onStateChange:
                (event) => {

                  stateHandlerRef
                    .current(event);
                },
            },
          }
        );
    };


    // 이미 YouTube API가 로딩되어 있는 경우
    if (
      window.YT?.Player
    ) {

      createPlayer();

    } else {

      // YouTube API Script가 없으면 추가
      if (
        !document.querySelector(
          'script[src="https://www.youtube.com/iframe_api"]'
        )
      ) {

        const script =
          document.createElement(
            'script'
          );


        script.src =
          'https://www.youtube.com/iframe_api';


        document.body.appendChild(
          script
        );
      }


      const previousReady =
        window.onYouTubeIframeAPIReady;


      window.onYouTubeIframeAPIReady =
        () => {

          if (
            typeof previousReady ===
            'function'
          ) {

            previousReady();
          }


          createPlayer();
        };
    }


    // 컴포넌트 제거 시 플레이어 정리
    return () => {

      cancelled = true;


      if (
        playerRef.current &&
        typeof playerRef.current.destroy ===
        'function'
      ) {

        playerRef.current.destroy();
      }


      playerRef.current = null;


      registerPlayer(null);
    };

  }, [
    registerPlayer
  ]);


  if (!videoId) {

    return (
      <div>
        재생할 음악이 없습니다.
      </div>
    );
  }


  return (

    <div
      style={{
        width: '100%',
        maxWidth: '720px',
        aspectRatio: '16 / 9',
      }}
    >

      <div
        ref={containerRef}
        style={{
          width: '100%',
          height: '100%',
        }}
      />

    </div>
  );
}