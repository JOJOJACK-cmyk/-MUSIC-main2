import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';

const PlayerContext = createContext(null);

export const PlayerProvider = ({ children }) => {

  const [currentTrack, setCurrentTrack] = useState(null);
  const [isPlaying, setIsPlaying] = useState(false);

  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  const [volume, setVolumeState] = useState(80);

  const [playlist, setPlaylist] = useState([]);

  const [isShuffle, setIsShuffle] = useState(false);
  const [isRepeat, setIsRepeat] = useState(false);

  // 실제 YouTube Player 객체를 저장
  const playerRef = useRef(null);

  // 현재 볼륨 기억
  const volumeRef = useRef(80);


  // YouTubePlayer에서 만들어진 실제 플레이어를 등록
  const registerPlayer = useCallback((player) => {

    playerRef.current = player;

    if (player && typeof player.setVolume === 'function') {
      player.setVolume(volumeRef.current);
    }

  }, []);


  // 실제 YouTube 재생시간 가져오기
  useEffect(() => {

    if (!isPlaying) return;

    const interval = setInterval(() => {

      const player = playerRef.current;

      if (!player) return;


      // 현재 재생시간
      if (typeof player.getCurrentTime === 'function') {

        const time = player.getCurrentTime() || 0;

        setCurrentTime(time);
      }


      // 실제 영상 전체 길이
      if (typeof player.getDuration === 'function') {

        const realDuration =
          player.getDuration() || 0;

        if (realDuration > 0) {

          setDuration(realDuration);
        }
      }

    }, 500);


    return () => clearInterval(interval);

  }, [isPlaying]);


  // 음악 선택
  const playTrack = (track) => {

    if (!track?.youtubeVideoId) {
      return;
    }

    setCurrentTrack(track);

    setCurrentTime(0);
    setDuration(0);

    // 실제 PLAYING 이벤트가 오면 true로 바뀜
    setIsPlaying(false);


    const player = playerRef.current;

    // 플레이어가 이미 만들어져 있다면
    if (
      player &&
      typeof player.loadVideoById === 'function'
    ) {

      player.loadVideoById(
        track.youtubeVideoId
      );
    }
  };


  // 재생 / 일시정지
  const togglePlay = () => {

    // 아직 선택된 음악이 없으면 첫 번째 곡 재생
    if (
      !currentTrack &&
      playlist.length > 0
    ) {

      playTrack(playlist[0]);

      return;
    }


    const player = playerRef.current;

    if (!player) {
      return;
    }


    const state =
      player.getPlayerState?.();


    // 1 = YouTube PLAYING 상태
    if (state === 1) {

      player.pauseVideo();

    } else {

      player.playVideo();
    }
  };


  // 다음 곡
  const handleNextTrack = () => {

    if (playlist.length === 0) {
      return;
    }


    const currentIndex =
      playlist.findIndex(
        (track) =>
          track.id === currentTrack?.id
      );


    // 현재 곡을 못 찾으면 첫 번째 곡
    if (currentIndex === -1) {

      playTrack(playlist[0]);

      return;
    }


if (isShuffle) {

  // 곡이 1개뿐이면 그대로 재생
  if (playlist.length === 1) {
    playTrack(playlist[0]);
    return;
  }

  let randomIndex;

  // 현재 재생 중인 곡과 다른 곡이 나올 때까지 다시 뽑음
  do {

    randomIndex =
      Math.floor(
        Math.random() *
        playlist.length
      );

  } while (
    playlist[randomIndex].id ===
    currentTrack?.id
  );

  playTrack(
    playlist[randomIndex]
  );

  return;
}


    const nextIndex =
      (currentIndex + 1) %
      playlist.length;


    playTrack(
      playlist[nextIndex]
    );
  };


  // 이전 곡
  const handlePrevTrack = () => {

    if (playlist.length === 0) {
      return;
    }


    const currentIndex =
      playlist.findIndex(
        (track) =>
          track.id === currentTrack?.id
      );


    if (currentIndex <= 0) {

      playTrack(
        playlist[
          playlist.length - 1
        ]
      );

    } else {

      playTrack(
        playlist[
          currentIndex - 1
        ]
      );
    }
  };


  // 진행바 클릭
  const seekTime = (percentage) => {

    const player =
      playerRef.current;

    if (
      !player ||
      duration <= 0
    ) {

      return;
    }


    const nextTime =
      (percentage / 100) *
      duration;


    player.seekTo(
      nextTime,
      true
    );


    setCurrentTime(
      nextTime
    );
  };


  // 실제 YouTube 볼륨 변경
  const changeVolume = (nextVolume) => {

    const safeVolume =
      Math.max(
        0,
        Math.min(
          100,
          nextVolume
        )
      );


    volumeRef.current =
      safeVolume;


    setVolumeState(
      safeVolume
    );


    const player =
      playerRef.current;


    if (
      player &&
      typeof player.setVolume === 'function'
    ) {

      player.setVolume(
        safeVolume
      );
    }
  };


  // 실제 YouTube 상태 변화
  const handlePlayerStateChange = (event) => {

    const state =
      event.data;


    // 0 = 영상 끝
    if (state === 0) {

      setIsPlaying(false);


      if (isRepeat) {

        event.target.seekTo(
          0,
          true
        );

        event.target.playVideo();

      } else {

        handleNextTrack();
      }

      return;
    }


    // 1 = 재생 중
    if (state === 1) {

      setIsPlaying(true);


      const realDuration =
        event.target.getDuration?.() || 0;


      setDuration(
        realDuration
      );

      return;
    }


    // 2 = 일시정지
    if (state === 2) {

      setIsPlaying(false);


      const time =
        event.target.getCurrentTime?.() || 0;


      setCurrentTime(
        time
      );
    }
  };


  return (

    <PlayerContext.Provider

      value={{

        currentTrack,

        isPlaying,

        currentTime,

        duration,

        volume,

        playlist,

        isShuffle,

        isRepeat,

        setPlaylist,

        playTrack,

        togglePlay,

        handleNextTrack,

        handlePrevTrack,

        seekTime,

        setVolume: changeVolume,

        setIsShuffle,

        setIsRepeat,

        registerPlayer,

        handlePlayerStateChange,
      }}

    >

      {children}

    </PlayerContext.Provider>
  );
};


export const usePlayer = () =>
  useContext(PlayerContext);