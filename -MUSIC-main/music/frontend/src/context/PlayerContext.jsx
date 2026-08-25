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

  // 30초 로그 수집을 위한 Ref 변수들
  const loggedTrackIdRef = useRef(null);
  const playTimeCounterRef = useRef(0);

  // 실제 YouTube Player 객체 저장
  const playerRef = useRef(null);
  const volumeRef = useRef(80);

  // YouTubePlayer에서 만들어진 실제 플레이어를 등록
  const registerPlayer = useCallback((player) => {
    playerRef.current = player;
    if (player && typeof player.setVolume === 'function') {
      player.setVolume(volumeRef.current);
    }
  }, []);

  const sendListenLog = async (musicId) => {
      try {
        console.log(`%c[Log Collector] 30초 달성! 백엔드로 청취 로그를 전송합니다. (musicId: ${musicId})`, 'color: #00ff00; font-weight: bold;');

        const response = await fetch('http://localhost:8080/api/v1/logs/listen', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            userId: 1, // 테스트 유저 ID
            musicId: Number(musicId),
            listenSeconds: 30,
          }),
        });

        if (response.ok) {
          console.log('%c[Log Collector] ✅ 백엔드 DB 저장 성공 (200 OK)!', 'color: #00ff00; font-weight: bold;');
        } else {
          console.error('[Log Collector] ❌ 전송 실패 응답 코드:', response.status);
        }
      } catch (err) {
        console.error('[Log Collector] ❌ 네트워크 에러:', err);
      }
    };

  // 실제 YouTube 재생시간 트래킹 & 30초 로그 수집 타이머
  useEffect(() => {
    if (!isPlaying || !currentTrack) return;

    const interval = setInterval(() => {
      const player = playerRef.current;
      if (!player) return;

      // 현재 재생시간 갱신
      if (typeof player.getCurrentTime === 'function') {
        const time = player.getCurrentTime() || 0;
        setCurrentTime(time);
      }

      // 전체 영상 길이 갱신
      if (typeof player.getDuration === 'function') {
        const realDuration = player.getDuration() || 0;
        if (realDuration > 0) {
          setDuration(realDuration);
        }
      }

      // 30초 누적 재생시간 체크 (곡마다 1회만 전송)
      playTimeCounterRef.current += 0.5;
      if (
        playTimeCounterRef.current >= 30 &&
        loggedTrackIdRef.current !== currentTrack.id
      ) {
        loggedTrackIdRef.current = currentTrack.id;
        sendListenLog(currentTrack.id);
      }
    }, 500);

    return () => clearInterval(interval);
  }, [isPlaying, currentTrack]);

  // 음악 선택 및 즉시 재생
  const playTrack = (track) => {
    if (!track?.youtubeVideoId) return;

    // 곡이 바뀌면 30초 누적 타이머 초기화
    if (currentTrack?.id !== track.id) {
      playTimeCounterRef.current = 0;
    }

    setCurrentTrack(track);
    setCurrentTime(0);
    setDuration(0);

    const player = playerRef.current;
    if (player && typeof player.loadVideoById === 'function') {
      player.loadVideoById(track.youtubeVideoId);
      player.playVideo();
    }
  };

  // 재생 / 일시정지 토글
  const togglePlay = () => {
    if (!currentTrack && playlist.length > 0) {
      playTrack(playlist[0]);
      return;
    }

    const player = playerRef.current;
    if (!player) return;

    const state = player.getPlayerState?.();
    if (state === 1) {
      player.pauseVideo();
    } else {
      player.playVideo();
    }
  };

  // 다음 곡
  const handleNextTrack = () => {
    if (playlist.length === 0) return;

    const currentIndex = playlist.findIndex((t) => t.id === currentTrack?.id);
    if (currentIndex === -1) {
      playTrack(playlist[0]);
      return;
    }

    if (isShuffle) {
      if (playlist.length === 1) {
        playTrack(playlist[0]);
        return;
      }
      let randomIndex;
      do {
        randomIndex = Math.floor(Math.random() * playlist.length);
      } while (playlist[randomIndex].id === currentTrack?.id);

      playTrack(playlist[randomIndex]);
      return;
    }

    const nextIndex = (currentIndex + 1) % playlist.length;
    playTrack(playlist[nextIndex]);
  };

  // 이전 곡
  const handlePrevTrack = () => {
    if (playlist.length === 0) return;

    const currentIndex = playlist.findIndex((t) => t.id === currentTrack?.id);
    if (currentIndex <= 0) {
      playTrack(playlist[playlist.length - 1]);
    } else {
      playTrack(playlist[currentIndex - 1]);
    }
  };

  // 진행바 탐색 (Seek)
  const seekTime = (percentage) => {
    const player = playerRef.current;
    if (!player || duration <= 0) return;

    const nextTime = (percentage / 100) * duration;
    player.seekTo(nextTime, true);
    setCurrentTime(nextTime);
  };

  // 볼륨 변경
  const changeVolume = (nextVolume) => {
    const safeVolume = Math.max(0, Math.min(100, nextVolume));
    volumeRef.current = safeVolume;
    setVolumeState(safeVolume);

    const player = playerRef.current;
    if (player && typeof player.setVolume === 'function') {
      player.setVolume(safeVolume);
    }
  };

  // 상태 변경 이벤트 핸들러
  const handlePlayerStateChange = (event) => {
    const state = event.data;

    // 0 = 영상 종료
    if (state === 0) {
      setIsPlaying(false);
      if (isRepeat) {
        event.target.seekTo(0, true);
        event.target.playVideo();
      } else {
        handleNextTrack();
      }
      return;
    }

    // 1 = 재생 중
    if (state === 1) {
      setIsPlaying(true);
      const realDuration = event.target.getDuration?.() || 0;
      setDuration(realDuration);
      return;
    }

    // 2 = 일시정지
    if (state === 2) {
      setIsPlaying(false);
      const time = event.target.getCurrentTime?.() || 0;
      setCurrentTime(time);
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

export const usePlayer = () => useContext(PlayerContext);