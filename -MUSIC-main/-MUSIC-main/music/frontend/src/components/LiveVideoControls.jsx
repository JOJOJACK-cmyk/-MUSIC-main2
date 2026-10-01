import React, { useEffect, useRef, useState } from 'react';

/** 라이브 영상용 커스텀 컨트롤 바 (네이티브 controls 대신 하나만). */
export default function LiveVideoControls({ videoRef, wrapRef, startedAt, viewerCount, manualPauseRef }) {
  const [playing, setPlaying] = useState(true);
  const [muted, setMuted] = useState(true);
  const [volume, setVolume] = useState(1);
  const [fs, setFs] = useState(false);
  const [now, setNow] = useState(Date.now());

  // 영상 상태 동기화 (지연 표시는 깜빡임 때문에 하지 않음)
  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    const sync = () => {
      setPlaying(!v.paused);
      setMuted(v.muted || v.volume === 0);
      setVolume(v.muted ? 0 : v.volume);
    };
    const evs = ['play', 'pause', 'volumechange'];
    evs.forEach((e) => v.addEventListener(e, sync));
    sync();
    return () => evs.forEach((e) => v.removeEventListener(e, sync));
  }, [videoRef]);

  // 경과시간
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    const onFs = () => setFs(Boolean(document.fullscreenElement));
    document.addEventListener('fullscreenchange', onFs);
    return () => document.removeEventListener('fullscreenchange', onFs);
  }, []);

  const v = () => videoRef.current;

  const togglePlay = () => {
    const el = v();
    if (!el) return;
    if (el.paused) {
      if (manualPauseRef) manualPauseRef.current = false;
      el.play().catch(() => {});
    } else {
      if (manualPauseRef) manualPauseRef.current = true; // 직접 멈춤 → 자동 재개 안 함
      el.pause();
    }
  };
  const toggleMute = () => {
    const el = v();
    if (!el) return;
    el.muted = !el.muted;
    if (!el.muted && el.volume === 0) el.volume = 0.5;
  };
  const changeVol = (val) => {
    const el = v();
    if (!el) return;
    el.volume = val;
    el.muted = val === 0;
  };
  const goLive = () => {
    const el = v();
    if (!el) return;
    if (manualPauseRef) manualPauseRef.current = false;
    try {
      // seekable 이 아직 안 채워졌으면(초기 버퍼링 중 등) buffered 로 대체
      const ranges = el.seekable && el.seekable.length ? el.seekable : el.buffered;
      if (ranges && ranges.length) {
        const end = ranges.end(ranges.length - 1);
        el.currentTime = Math.max(0, end - 0.5);
      }
    } catch (_) {}
    el.play().catch(() => {});
  };
  const toggleFs = () => {
    const w = wrapRef.current;
    if (!w) return;
    if (document.fullscreenElement) document.exitFullscreen?.();
    else w.requestFullscreen?.();
  };

  let elapsed = '';
  if (startedAt) {
    const s = Math.max(0, Math.floor((now - new Date(startedAt).getTime()) / 1000));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    elapsed = h > 0
      ? `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
      : `${m}:${String(sec).padStart(2, '0')}`;
  }

  return (
    <div className="lvc">
      <button className="lvc-btn" onClick={togglePlay} aria-label={playing ? '일시정지' : '재생'}>
        <i className={`fa-solid ${playing ? 'fa-pause' : 'fa-play'}`} />
      </button>

      <button className="lvc-btn" onClick={toggleMute} aria-label="음소거">
        <i className={`fa-solid ${muted ? 'fa-volume-xmark' : volume > 0.5 ? 'fa-volume-high' : 'fa-volume-low'}`} />
      </button>
      <input
        className="lvc-vol"
        type="range" min="0" max="1" step="0.05"
        value={muted ? 0 : volume}
        onChange={(e) => changeVol(Number(e.target.value))}
      />

      <button className="lvc-live on" onClick={goLive} title="실시간 지점으로 이동">
        <span className="live-dot" /> LIVE
      </button>

      <span className="lvc-time">{elapsed}</span>
      <span className="lvc-track"><span className="lvc-glow" /></span>
      <span className="lvc-viewers">
        <i className="fa-solid fa-user-group" /> {(viewerCount ?? 0).toLocaleString()}
      </span>

      <button className="lvc-btn" onClick={toggleFs} aria-label="전체화면">
        <i className={`fa-solid ${fs ? 'fa-compress' : 'fa-expand'}`} />
      </button>
    </div>
  );
}
