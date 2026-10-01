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

// 무료(비로그인 또는 미결제) 회원의 세션 누적 미리듣기 허용 시간(초)
export const PREVIEW_LIMIT_SECONDS = 60;

// 탭 폐기(브라우저 메모리 절약)·새로고침 시 재생 상태를 복원하기 위한 저장 키
const PLAYER_STATE_KEY = 'player:state';

const readSavedState = () => {
  try {
    const raw = sessionStorage.getItem(PLAYER_STATE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch (_) {
    return null;
  }
};

// 미리듣기 누적 시간은 (사용자, 날짜)별로 localStorage 에 저장한다 — 새로고침으로 초기화되지 않게
const previewKey = (email) => `player:preview:${email || 'anon'}:${new Date().toISOString().slice(0, 10)}`;
const readPreviewSeconds = (email) => {
  try {
    const v = Number(localStorage.getItem(previewKey(email)));
    return Number.isFinite(v) && v > 0 ? v : 0;
  } catch (_) {
    return 0;
  }
};
const writePreviewSeconds = (email, seconds) => {
  try { localStorage.setItem(previewKey(email), String(seconds)); } catch (_) {}
};

export const PlayerProvider = ({ children }) => {
  const { user, hasFullAccess, limitedPlay, applySongClaim } = useAuth();

  const [saved] = useState(readSavedState);

  const [currentTrack, setCurrentTrack] = useState(saved?.currentTrack ?? null);
  const [isPlaying, setIsPlaying] = useState(false);

  // =========================
  // 무료 회원 미리듣기 제한
  // =========================
  // 세션 동안 실제로 재생된 시간의 누적(초). 곡을 바꿔도 초기화되지 않는다.
  const previewSecondsRef = useRef(readPreviewSeconds(null));
  // 누적 60초를 초과해 재생이 잠긴 상태 (결제/로그인 전까지 재생 불가)
  const [previewLocked, setPreviewLocked] = useState(false);
  // 잠긴 이유: 'preview' (무료 미리듣기 소진) | 'songLimit' (곡 수 제한 이용권의 곡을 다 씀)
  const [lockReason, setLockReason] = useState('preview');

  const [currentTime, setCurrentTime] = useState(saved?.currentTime ?? 0);
  const [duration, setDuration] = useState(0);
  const currentTimeRef = useRef(saved?.currentTime ?? 0);

  const [volume, setVolumeState] = useState(
    typeof saved?.volume === 'number' ? saved.volume : 80
  );
  const [playlist, setPlaylist] = useState(
    Array.isArray(saved?.playlist) ? saved.playlist : []
  );

  const [isShuffle, setIsShuffle] = useState(Boolean(saved?.isShuffle));
  const [isRepeat, setIsRepeat] = useState(Boolean(saved?.isRepeat));

  // 복원된 재생 위치 (YouTubePlayer 가 최초 1회 소비)
  const restoreSeekRef = useRef(
    saved?.currentTrack?.youtubeVideoId && (saved?.currentTime ?? 0) > 3
      ? { videoId: saved.currentTrack.youtubeVideoId, seconds: Math.floor(saved.currentTime) }
      : null
  );

  // =========================
  // 청취 로그 관련
  // =========================
  const loggedTrackIdRef = useRef(null);
  const playTimeCounterRef = useRef(0);
  const isSendingLogRef = useRef(false);

  // 연속 재생 실패(임베드 불가/삭제된 영상 등) 카운터 — 무한 스킵 방지
  const consecutiveErrorRef = useRef(0);

  // 곡 종료 처리 중복 방지 + 종료 핸들러 (백그라운드 탭에서 'ended' 이벤트를 놓칠 때 폴백)
  const endedGuardRef = useRef(null);
  const endedHandlerRef = useRef(null);
  // 곡 길이 기반 자동 넘김 타이머 — 다른 탭/화면을 보고 있어도 시간이 되면 다음 곡으로
  const advanceTimerRef = useRef(null);
  const [seekVersion, setSeekVersion] = useState(0); // seek 시 타이머 재계산 트리거
  const currentTrackRef = useRef(currentTrack);
  const playlistRef = useRef(playlist);
  const isRepeatRef = useRef(isRepeat);
  const isPlayingRef = useRef(isPlaying);
  // 지금 곡이 끝나면 강제로 재생할 곡 id (예: 라이브 "1위 곡 재생")
  const forceNextRef = useRef(null);
  useEffect(() => { currentTrackRef.current = currentTrack; }, [currentTrack]);
  useEffect(() => { playlistRef.current = playlist; }, [playlist]);
  useEffect(() => { isRepeatRef.current = isRepeat; }, [isRepeat]);
  useEffect(() => { isPlayingRef.current = isPlaying; }, [isPlaying]);

  // =========================
  // YouTube Player 관련
  // =========================
  const playerRef = useRef(null);
  const volumeRef = useRef(typeof saved?.volume === 'number' ? saved.volume : 80);

  const registerPlayer = useCallback((player) => {
    playerRef.current = player;

    if (player && typeof player.setVolume === 'function') {
      player.setVolume(volumeRef.current);
    }
  }, []);

  // YouTubePlayer 가 플레이어 생성 시 복원 위치를 조회한다.
  //   (StrictMode 이중 마운트 대비 — 즉시 비우지 않고, 실제 재생 시작/곡 변경 시 비운다)
  const consumeRestoreSeek = useCallback((videoId) => {
    const r = restoreSeekRef.current;
    if (r && r.videoId === videoId && r.seconds > 3) return r.seconds;
    return 0;
  }, []);

  // =========================
  // 재생 상태 영속화 (탭 폐기/새로고침 대비)
  // =========================
  const persistState = useCallback(() => {
    try {
      sessionStorage.setItem(
        PLAYER_STATE_KEY,
        JSON.stringify({
          currentTrack,
          currentTime: currentTimeRef.current,
          playlist,
          volume,
          isShuffle,
          isRepeat,
        })
      );
    } catch (_) {}
  }, [currentTrack, playlist, volume, isShuffle, isRepeat]);

  const persistRef = useRef(persistState);
  persistRef.current = persistState;

  // 곡/목록/볼륨/모드가 바뀌면 즉시 저장
  useEffect(() => {
    persistState();
  }, [persistState]);

  // 다른 탭을 보다 돌아왔을 때, 백그라운드에서 곡이 이미 끝나 있으면 다음 곡으로 이어준다.
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState !== 'visible') return;
      const player = playerRef.current;
      if (!player || !currentTrack) return;
      try {
        if (player.getPlayerState?.() === 0 && endedGuardRef.current !== currentTrack.id) {
          endedGuardRef.current = currentTrack.id;
          endedHandlerRef.current?.();
        }
      } catch (_) {}
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [currentTrack]);

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
        '/api/v1/logs/listen',
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
  // 이용권 상태가 바뀌면(결제/로그인) 미리듣기 카운터 초기화
  // =========================
  useEffect(() => {
    if (hasFullAccess) {
      previewSecondsRef.current = 0;
      setPreviewLocked(false);
    }
  }, [hasFullAccess]);

  // 곡 수 제한 이용권을 새로 샀으면(곡이 남아 있으면) 잠금을 풀어 준다
  const limitedLeft = limitedPlay ? limitedPlay.limit - limitedPlay.used : 0;
  useEffect(() => {
    if (limitedLeft > 0) setPreviewLocked(false);
  }, [limitedLeft]);

  // 로그인 계정이 바뀌면(로그인/로그아웃) 그 계정의 오늘 미리듣기 누적값을 불러온다
  useEffect(() => {
    previewSecondsRef.current = readPreviewSeconds(user?.email);
    setPreviewLocked(false);
  }, [user?.email]);

  // 무료 회원의 미리듣기 잠금: 플레이어를 실제로 정지하고 곡을 비운다.
  const lockPreview = useCallback(() => {
    const player = playerRef.current;
    try {
      player?.pauseVideo?.();
      player?.stopVideo?.();
    } catch (e) {}
    setIsPlaying(false);
    setCurrentTime(0);
    currentTimeRef.current = 0;
    setCurrentTrack(null); // YouTube IFrame 언마운트 → 영상 완전 정지
    playTimeCounterRef.current = 0;
    setPreviewLocked(true);
  }, []);

  // =========================
  // 이 곡을 전곡 재생할 수 있는지 — 무제한 이용권 · 관리자, 또는 곡 수 제한 이용권에서
  // 이미 차감된 곡이거나 아직 곡이 남아 있으면. (아니면 무료 미리듣기 규칙)
  // =========================
  const limitedRef = useRef(limitedPlay);
  limitedRef.current = limitedPlay;
  const canFullPlay = useCallback((track) => {
    if (hasFullAccess) return true;
    const lp = limitedRef.current;
    if (!lp || !track) return false;
    return lp.claimedIds.some((x) => String(x) === String(track.id)) || lp.used < lp.limit;
  }, [hasFullAccess]);
  const isClaimed = (track) => Boolean(limitedRef.current?.claimedIds.some((x) => String(x) === String(track?.id)));

  // 곡 수 제한 이용권: 30초 재생 시점에 1곡 차감 (같은 곡은 서버가 다시 차감하지 않음)
  const claimSong = useCallback(async (musicId) => {
    const token = localStorage.getItem('token') || localStorage.getItem('accessToken');
    try {
      const res = await fetch('/api/v1/payments/play-claim', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        credentials: 'include',
        body: JSON.stringify({ musicId: Number(musicId) }),
      });
      if (!res.ok) return null;
      const result = await res.json();
      applySongClaim?.(musicId, result);
      return result;
    } catch (_) {
      return null;
    }
  }, [applySongClaim]);

  // 미리듣기/곡 수 소진으로 잠글 때 이유를 함께 남긴다
  const lockFor = useCallback((reason) => {
    setLockReason(reason);
    lockPreview();
  }, [lockPreview]);

  // =========================
  // 실제 재생시간 추적 및 세션 누적 1분(60초) 제한 통제
  // =========================
  useEffect(() => {
    if (!isPlaying || !currentTrack) {
      return;
    }

    // 이 곡은 전곡 재생인지 (곡 수 제한 이용권은 곡마다 다름 — 차감 결과가 오면 이 효과가 다시 돈다)
    const fullPlay = canFullPlay(currentTrack);
    let tick = 0;
    const interval = setInterval(() => {
      const player = playerRef.current;
      if (!player) return;

      let time = 0;
      if (typeof player.getCurrentTime === 'function') {
        time = player.getCurrentTime() || 0;
        setCurrentTime(time);
        currentTimeRef.current = time;
      }

      // 다른 탭/화면을 보고 있어 YT 'ended' 이벤트가 안 오는 경우 대비한 폴백:
      // 상태가 '종료(0)'거나, 끝에서 멈춰있으면(2) 직접 다음 곡으로 넘긴다.
      // (재생 중 상태(1)는 건드리지 않아 정상 재생/반복을 방해하지 않음)
      const ps = typeof player.getPlayerState === 'function' ? player.getPlayerState() : null;
      const dur = typeof player.getDuration === 'function' ? (player.getDuration() || 0) : 0;
      if (ps === 0 || (ps === 2 && dur > 0 && time >= dur - 1.5)) {
        if (endedGuardRef.current !== currentTrack.id) {
          endedGuardRef.current = currentTrack.id;
          endedHandlerRef.current?.();
        }
        return;
      }

      // 💡 무료(비로그인 또는 미결제) 회원 제한:
      //    ① 세션 누적 재생시간이 60초를 넘거나
      //    ② 재생 위치(스크럽/강제 건너뛰기 포함)가 60초를 넘으면 즉시 잠금
      if (!fullPlay) {
        previewSecondsRef.current += 0.5;
        // 2초마다 저장 (새로고침해도 누적값 유지)
        if (previewSecondsRef.current % 2 === 0) writePreviewSeconds(user?.email, previewSecondsRef.current);
        if (
          previewSecondsRef.current >= PREVIEW_LIMIT_SECONDS ||
          time >= PREVIEW_LIMIT_SECONDS
        ) {
          writePreviewSeconds(user?.email, previewSecondsRef.current);
          lockFor(limitedRef.current ? 'songLimit' : 'preview');
          return;
        }
      }

      if (typeof player.getDuration === 'function') {
        const realDuration = player.getDuration() || 0;
        if (realDuration > 0) {
          setDuration(realDuration);
        }
      }

      playTimeCounterRef.current += 0.5;

      // 약 2초마다 재생 상태를 저장 (탭이 갑자기 폐기돼도 위치 복원)
      tick += 1;
      if (tick % 4 === 0) persistRef.current();

      if (
        playTimeCounterRef.current >= 30 &&
        loggedTrackIdRef.current !== currentTrack.id &&
        !isSendingLogRef.current
      ) {
        const trackId = currentTrack.id;
        loggedTrackIdRef.current = trackId; // 응답을 기다리는 동안 다시 보내지 않게
        (async () => {
          // 곡 수 제한 이용권: 아직 차감 안 된 곡이면 먼저 1곡 차감 — 한도를 넘었으면 이 곡은 미리듣기로 전환
          if (fullPlay && !hasFullAccess && !isClaimed(currentTrack)) {
            const r = await claimSong(trackId);
            if (!r?.allowed) return;
          }
          if (!fullPlay) return; // 미리듣기는 청취 기록 · 차트에 넣지 않는다 (서버도 거른다)
          const success = await sendListenLog(trackId);
          if (!success && loggedTrackIdRef.current === trackId) loggedTrackIdRef.current = null;
        })();
      }
    }, 500);

    return () => {
      clearInterval(interval);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isPlaying, currentTrack, sendListenLog, user, hasFullAccess, limitedPlay, canFullPlay, claimSong, lockFor]);

  // =========================
  // 곡 길이 기반 자동 넘김 타이머
  //   YT 'ended' 이벤트는 탭이 백그라운드면 지연/유실되므로(돌아와야 넘어감),
  //   재생 시작 시점에 "남은 시간 후 다음 곡" 타이머를 예약해 둔다.
  //   setTimeout 은 백그라운드에서도(다소 늦더라도) 반드시 한 번은 실행된다.
  // =========================
  useEffect(() => {
    if (advanceTimerRef.current) {
      clearTimeout(advanceTimerRef.current);
      advanceTimerRef.current = null;
    }
    if (!isPlaying || !currentTrack || isRepeat || duration <= 0) return;

    const remainingMs = Math.max(0, (duration - currentTimeRef.current) * 1000) + 1200;
    const trackId = currentTrack.id;
    advanceTimerRef.current = setTimeout(() => {
      advanceTimerRef.current = null;
      // 타이머 발화 시점에도 여전히 같은 곡이고 아직 종료 처리 전이면 다음 곡으로
      if (currentTrackRef.current?.id !== trackId) return;
      if (endedGuardRef.current === trackId) return;
      endedGuardRef.current = trackId;
      endedHandlerRef.current?.();
    }, remainingMs);

    return () => {
      if (advanceTimerRef.current) {
        clearTimeout(advanceTimerRef.current);
        advanceTimerRef.current = null;
      }
    };
  }, [isPlaying, currentTrack, isRepeat, duration, seekVersion]);

  // =========================
  // 음악 선택 / 재생
  //   두 번째 인자로 목록(queue)을 넘기면 그 목록을 재생목록으로 함께 교체한다.
  //   (보관함/차트/각 섹션에서 재생 시 그 목록 안에서만 다음곡이 이어지도록)
  // =========================
  const playTrack = useCallback((track, queue) => {
    if (Array.isArray(queue) && queue.length > 0) {
      setPlaylist(queue);
    }

    if (!track?.youtubeVideoId) {
      setCurrentTrack(null);
      return;
    }

    // 전곡 재생 대상이 아닌데(무료 · 곡 수 소진) 미리듣기 60초도 다 썼으면 재생 자체를 차단
    if (!canFullPlay(track) && previewSecondsRef.current >= PREVIEW_LIMIT_SECONDS) {
      setLockReason(limitedRef.current ? 'songLimit' : 'preview');
      setPreviewLocked(true);
      return;
    }

    // 사용자가 직접 고른 곡이므로 연속 실패/종료 가드 리셋
    consecutiveErrorRef.current = 0;
    endedGuardRef.current = null;
    restoreSeekRef.current = null;

    setCurrentTrack((prev) => {
      if (prev?.id !== track.id) {
        playTimeCounterRef.current = 0;
      }
      return track;
    });
    setCurrentTime(0);
    currentTimeRef.current = 0;
    setDuration(0);

    const player = playerRef.current;
    if (player && typeof player.loadVideoById === 'function') {
      player.loadVideoById(track.youtubeVideoId);
      player.playVideo();
    }
  }, [canFullPlay]);

  // 두 목록이 같은 곡 순서인지 (id 기준)
  const queuesEqual = (a, b) => {
    if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) {
      if (String(a[i]?.id) !== String(b[i]?.id)) return false;
    }
    return true;
  };

  // =========================
  // 카드/행 클릭 진입점.
  //   - 다른 곡  → 그 곡 재생 + 목록(queue) 교체
  //   - 같은 곡, 다른 목록에서 누름 → 재생은 유지하고 "이 목록"으로 전환
  //     (그래서 다음 곡부터 그 목록을 따라감)
  //   - 같은 곡, 같은 목록 → 재생/일시정지 토글
  // =========================
  const selectTrack = useCallback((track, queue) => {
    const list = Array.isArray(queue) && queue.length > 0 ? queue : null;
    const cur = currentTrackRef.current;
    if (cur && track && String(cur.id) === String(track.id)) {
      if (list && !queuesEqual(list, playlistRef.current)) {
        playTrack(track, list); // 목록 전환 + 처음부터 다시 재생
      } else {
        const player = playerRef.current;
        const st = player?.getPlayerState?.();
        if (st === 1) player?.pauseVideo?.();
        else player?.playVideo?.();
      }
      return;
    }
    playTrack(track, list);
  }, [playTrack]);

  // =========================
  // 지금 곡이 끝나면 track 을 재생하고, 그 뒤로는 followList 를 이어서 재생.
  //   현재 재생 중인 곡이 없으면 바로 재생.
  //   (라이브 "1위 곡 재생" → 곡 끝나고 winner → 이후 실시간 인기곡)
  // =========================
  const queueTrackThenList = useCallback((track, followList) => {
    if (!track?.youtubeVideoId) return;
    const rest = (Array.isArray(followList) ? followList : [])
      .filter((t) => t?.youtubeVideoId && String(t.id) !== String(track.id));
    const nextPlaylist = [track, ...rest];
    setPlaylist(nextPlaylist);
    playlistRef.current = nextPlaylist;

    if (!currentTrackRef.current || !isPlayingRef.current) {
      forceNextRef.current = null;
      playTrack(track, nextPlaylist);
    } else {
      forceNextRef.current = track.id; // 지금 곡 끝나면 이 곡부터
    }
  }, [playTrack]);

  // =========================
  // 재생 / 일시정지
  // =========================
  const togglePlay = () => {
    // 이 곡을 전곡 재생할 수 없고 미리듣기 시간도 다 쓴 경우 재생 차단
    if (!canFullPlay(currentTrack || playlist[0]) && previewSecondsRef.current >= PREVIEW_LIMIT_SECONDS) {
      setLockReason(limitedRef.current ? 'songLimit' : 'preview');
      setPreviewLocked(true);
      return;
    }

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
    // "1위 곡 재생" 등으로 예약된 곡이 있으면(자동 종료뿐 아니라 수동 "다음 곡" 클릭에도) 그 곡부터
    if (forceNextRef.current != null) {
      const wid = forceNextRef.current;
      forceNextRef.current = null;
      const w = (playlistRef.current || []).find((t) => String(t?.id) === String(wid));
      if (w) { playTrack(w); return; }
    }

    if (playlist.length === 0) return;

    const currentIndex = playlist.findIndex(
      (track) => track.id === currentTrack?.id
    );

    if (isShuffle && playlist.length > 1) {
      let randomIndex;
      do {
        randomIndex = Math.floor(Math.random() * playlist.length);
      } while (randomIndex === currentIndex);
      playTrack(playlist[randomIndex]);
      return;
    }

    // 현재 곡이 목록에 있으면 다음 곡, 없으면(엣지 케이스) 목록 첫 곡부터.
    const next = currentIndex === -1 ? 0 : (currentIndex + 1) % playlist.length;
    playTrack(playlist[next]);
  };

  // =========================
  // 이전 곡
  // =========================
  const handlePrevTrack = () => {
    if (playlist.length === 0) return;

    const currentIndex = playlist.findIndex(
      (track) => track.id === currentTrack?.id
    );

    if (isShuffle && playlist.length > 1) {
      let randomIndex;
      do {
        randomIndex = Math.floor(Math.random() * playlist.length);
      } while (randomIndex === currentIndex);
      playTrack(playlist[randomIndex]);
      return;
    }

    (currentIndex <= 0)
      ? playTrack(playlist[playlist.length - 1])
      : playTrack(playlist[currentIndex - 1]);
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
    currentTimeRef.current = nextTime;
    setSeekVersion((v) => v + 1); // 자동 넘김 타이머 재계산
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
  // 곡 종료 처리 (YT 'ended' 이벤트 + 백그라운드 폴백 공용)
  // =========================
  const handleTrackEnded = () => {
    const player = playerRef.current;
    if (isRepeat) {
      try { player?.seekTo?.(0, true); player?.playVideo?.(); } catch (_) {}
      endedGuardRef.current = null;
      return;
    }
    setIsPlaying(false);

    // "1위 곡 재생" 등으로 예약된 곡이 있으면 그 곡부터
    if (forceNextRef.current != null) {
      const wid = forceNextRef.current;
      forceNextRef.current = null;
      const w = (playlistRef.current || []).find((t) => String(t?.id) === String(wid));
      if (w) { playTrack(w); return; }
    }
    handleNextTrack();
  };
  endedHandlerRef.current = handleTrackEnded;

  // =========================
  // YouTube Player 상태 변경
  // =========================
  const handlePlayerStateChange = (event) => {
    const state = event.data;

    if (state === 0) {
      setIsPlaying(false);
      if (endedGuardRef.current !== currentTrack?.id) {
        endedGuardRef.current = currentTrack?.id ?? null;
        handleTrackEnded();
      }
      return;
    }

    if (state === 1) {
      // 정상 재생 진입 → 연속 실패 카운터 & 복원 위치 & 종료 가드 리셋
      consecutiveErrorRef.current = 0;
      restoreSeekRef.current = null;
      endedGuardRef.current = null;
      setIsPlaying(true);
      const realDuration = event.target.getDuration?.() || 0;
      setDuration(realDuration);
      return;
    }

    if (state === 2) {
      setIsPlaying(false);
      const time = event.target.getCurrentTime?.() || 0;
      setCurrentTime(time);
      currentTimeRef.current = time;
      persistRef.current();
    }
  };

  // =========================
  // YouTube Player 재생 에러 (2·5·100·101·150 = 잘못된 ID/임베드 불가/삭제됨)
  //   → 다음 곡으로 자동 스킵. 연속 실패가 목록 길이를 넘으면 중단.
  // =========================
  const handlePlayerError = useCallback((code) => {
    const skippable = [2, 5, 100, 101, 150];
    console.warn('[YouTube] 재생 에러 코드', code, '- 다음 곡으로 넘어갑니다.');
    if (!skippable.includes(Number(code))) return;

    consecutiveErrorRef.current += 1;
    const limit = Math.min(8, Math.max(3, playlist.length));
    if (consecutiveErrorRef.current > limit) {
      console.warn('[YouTube] 연속 재생 실패가 많아 자동 스킵을 중단합니다.');
      setIsPlaying(false);
      return;
    }
    handleNextTrack();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playlist]);

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
        previewLocked,
        lockReason,
        hasFullAccess,
        limitedPlay,
        canFullPlay,
        previewLimitSeconds: PREVIEW_LIMIT_SECONDS,
        dismissPreviewLock: () => setPreviewLocked(false),
        setPlaylist,
        playTrack,
        selectTrack,
        queueTrackThenList,
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
        handlePlayerError,
        consumeRestoreSeek,
      }}
    >
      {children}
    </PlayerContext.Provider>
  );
};

export const usePlayer = () => useContext(PlayerContext);
