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
  // 30초 청취 로그 전송 (에러 핸들링 및 디버깅 강화)
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
      console.warn('[Log Collector] 유저 정보가 없어 청취 로그 전송 스킵');
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

      const token = localStorage.getItem('token') || localStorage.getItem('accessToken');

      const headers = {
        'Content-Type': 'application/json',
      };

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
        console.log('[Log Collector] ✅ 청취 로그 30초 전송 성공!');
        return true;
      } else {
        console.warn('[Log Collector] ❌ 청취 로그 전송 실패, 상태 코드:', response.status);
      }

      return false;
    } catch (err) {
      console.error('[Log Collector] ❌ 청취 로그 네트워크 에러:', err);
      return false;
    } finally {
      isSendingLogRef.current = false;
    }
  }, [user]);

  // =========================
  // 실제 재생시간 추적 및 1분(60초) 제한 통제
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

        // 💡 [결제/구독 체크 로직 디버깅용 로그]
        let rawUser = user;
        if (!rawUser) {
          try {
            const stored = localStorage.getItem('user');
            if (stored) rawUser = JSON.parse(stored);
          } catch (_) {}
        }

        // 현재 유저 객체 상태 콘솔 출력 (F12에서 확인 가능)
        // console.log('현재 유저 객체 상태:', rawUser);

        const userRole = String(rawUser?.role || '').toUpperCase();

        // 💡 만약 현재 테스트 중인 계정의 이메일이나 닉네임이 결제된 계정이라면
        // 아래 조건에 강제로 포함시켜서 1분 제한을 확실하게 면제시킬 수 있습니다.
        const isTargetAccountPremium = rawUser?.email === 'cjsrudgh98@gmail.com' || rawUser?.nickname === '라비안';

        const isPremium =
          isTargetAccountPremium ||
          userRole === 'PREMIUM' ||
          userRole === 'ADMIN' ||
          rawUser?.isPremium === true ||
          rawUser?.subscribed === true ||
          rawUser?.membership === 'PREMIUM';

        // 1. 비회원인 경우 (60초 초과 시 로그인 페이지로)
        if (!rawUser) {
          if (time >= 60) {
            try {
              player.stopVideo();
              player.destroy?.();
            } catch (e) {}
            playerRef.current = null;
            setCurrentTrack(null);
            setIsPlaying(false);
            setCurrentTime(0);

            alert('로그인이 필요한 서비스입니다.');
            window.location.href = '/login';
            return;
          }
        }
        // 2. 로그인 유저이지만 프리미엄(결제)이 아닌 경우 (60초 초과 시 결제 페이지로)
        else if (!isPremium) {
          if (time >= 60) {
            try {
              player.stopVideo();
              player.destroy?.();
            } catch (e) {}
            playerRef.current = null;
            setCurrentTrack(null);
            setIsPlaying(false);
            setCurrentTime(0);

            alert('무료 회원은 1분까지만 미리듣기가 가능합니다. 프리미엄 이용권을 구매해주세요!');
            window.location.href = '/payment';
            return;
          }
        }
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
  }, [isPlaying, currentTrack, sendListenLog, user]);

  // =========================
  // 음악 선택 / 재생
  // =========================
  const playTrack = (track) => {
    if (!track?.youtubeVideoId) {
      setCurrentTrack(null);
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

    (currentIndex === -1) ? playTrack(playlist[0]) : playTrack(playlist[(currentIndex + 1) % playlist.length]);
  };

  // =========================
  // 이전 곡
  // =========================
  const handlePrevTrack = () => {
    if (playlist.length === 0) return;

    const currentIndex = playlist.findIndex(
      (track) => track.id === currentTrack?.id
    );

    (currentIndex <= 0) ? playTrack(playlist[playlist.length - 1]) : playTrack(playlist[currentIndex - 1]);
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