import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';

import { useAuth } from './AuthContext';

const PlayerContext = createContext(null);

export const PlayerProvider = ({ children }) => {
  const { user } = useAuth();

  const [currentTrack, setCurrentTrack] = useState(null);
  const [isPlaying, setIsPlaying] = useState(false);

  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  const [volume, setVolumeState] = useState(80);
  const [playlist, setPlaylist] = useState([]);

  const [isShuffle, setIsShuffle] = useState(false);
  const [isRepeat, setIsRepeat] = useState(false);

  // =========================
  // 청취 로그 관련
  // =========================
  const loggedTrackIdRef = useRef(null);
  const playTimeCounterRef = useRef(0);
  const isSendingLogRef = useRef(false);

  // =========================
  // YouTube Player 관련
  // =========================
  const playerRef = useRef(null);
  const volumeRef = useRef(80);

  const registerPlayer = useCallback((player) => {
    playerRef.current = player;

    if (player && typeof player.setVolume === 'function') {
      player.setVolume(volumeRef.current);
    }
  }, []);

  // =========================
  // 30초 청취 로그 전송
  // =========================
  const sendListenLog = useCallback(async (musicId) => {
    let rawUser = user;
    if (!rawUser) {
      try {
        const stored = localStorage.getItem('user');
        if (stored) rawUser = JSON.parse(stored);
      } catch (_) {}
    }

    const rawId = rawUser?.id || rawUser?.userId || rawUser?.username || rawUser?.email;

    if (!rawId) {
      console.warn(
        '[Log Collector] 로그인 사용자 정보가 없어 청취 로그를 전송하지 않습니다.'
      );
      return false;
    }

    if (isSendingLogRef.current) {
      return false;
    }

    isSendingLogRef.current = true;

    try {
      const isNumericId = !isNaN(rawId) && !String(rawId).includes('@');

      const requestBody = {
        musicId: Number(musicId),
        listenSeconds: 30,
      };

      if (isNumericId) {
        requestBody.userId = Number(rawId);
      } else {
        requestBody.email = String(rawId);
      }

      // 💡 로컬스토리지에서 인증 토큰 가져오기 (프로젝트에 맞게 'token' 또는 'accessToken' 사용)
      const token = localStorage.getItem('token') || localStorage.getItem('accessToken');

      const headers = {
        'Content-Type': 'application/json',
      };

      // 💡 토큰이 존재할 경우 인증 헤더 추가
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(
        'http://localhost:8080/api/v1/logs/listen',
        {
          method: 'POST',
          headers: headers,
          credentials: 'include',
          body: JSON.stringify(requestBody),
        }
      );

      if (response.ok) {
        console.log('[Log Collector] ✅ 백엔드 DB 저장 성공 (200 OK)');
        return true;
      }

      console.error('[Log Collector] ❌ 전송 실패 응답 코드:', response.status);
      return false;

    } catch (err) {
      console.error('[Log Collector] ❌ 네트워크 에러:', err);
      return false;
    } finally {
      isSendingLogRef.current = false;
    }
  }, [user]);

  // =========================
  // 실제 재생시간 추적
  // =========================
  useEffect(() => {
    if (!isPlaying || !currentTrack) {
      return;
    }

    const interval = setInterval(() => {
      const player = playerRef.current;
      if (!player) return;

      if (typeof player.getCurrentTime === 'function') {
        const time = player.getCurrentTime() || 0;
        setCurrentTime(time);
      }

      if (typeof player.getDuration === 'function') {
        const realDuration = player.getDuration() || 0;
        if (realDuration > 0) {
          setDuration(realDuration);
        }
      }

      playTimeCounterRef.current += 0.5;

      if (
        playTimeCounterRef.current >= 30 &&
        loggedTrackIdRef.current !== currentTrack.id &&
        !isSendingLogRef.current
      ) {
        sendListenLog(currentTrack.id).then((success) => {
          if (success) {
            loggedTrackIdRef.current = currentTrack.id;
          }
        });
      }
    }, 500);

    return () => {
      clearInterval(interval);
    };
  }, [isPlaying, currentTrack, sendListenLog]);

  // =========================
  // 음악 선택 / 재생
  // =========================
  const playTrack = (track) => {
    if (!track?.youtubeVideoId) {
      return;
    }

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

  // =========================
  // 재생 / 일시정지
  // =========================
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

  // =========================
  // 다음 곡
  // =========================
  const handleNextTrack = () => {
    if (playlist.length === 0) return;

    const currentIndex = playlist.findIndex(
      (track) => track.id === currentTrack?.id
    );

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

  // =========================
  // 이전 곡
  // =========================
  const handlePrevTrack = () => {
    if (playlist.length === 0) return;

    const currentIndex = playlist.findIndex(
      (track) => track.id === currentTrack?.id
    );

    if (currentIndex <= 0) {
      playTrack(playlist[playlist.length - 1]);
    } else {
      playTrack(playlist[currentIndex - 1]);
    }
  };

  // =========================
  // 진행바 이동
  // =========================
  const seekTime = (percentage) => {
    const player = playerRef.current;
    if (!player || duration <= 0) return;

    const nextTime = (percentage / 100) * duration;
    player.seekTo(nextTime, true);
    setCurrentTime(nextTime);
  };

  // =========================
  // 볼륨
  // =========================
  const changeVolume = (nextVolume) => {
    const safeVolume = Math.max(0, Math.min(100, nextVolume));
    volumeRef.current = safeVolume;
    setVolumeState(safeVolume);

    const player = playerRef.current;
    if (player && typeof player.setVolume === 'function') {
      player.setVolume(safeVolume);
    }
  };

  // =========================
  // YouTube Player 상태 변경
  // =========================
  const handlePlayerStateChange = (event) => {
    const state = event.data;

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

    if (state === 1) {
      setIsPlaying(true);
      const realDuration = event.target.getDuration?.() || 0;
      setDuration(realDuration);
      return;
    }

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
        playNext: handleNextTrack,
        playPrevious: handlePrevTrack,
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