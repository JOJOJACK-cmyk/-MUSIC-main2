import React, { useEffect, useRef } from 'react';
import Hls from 'hls.js';
import { usePlayer } from '../context/PlayerContext';

export default function HlsAudioPlayer() {
  // PlayerContext에서 다음 곡, 이전 곡 제어 함수(playNext, playPrevious)도 함께 가져옵니다.
  const { currentTrack, isPlaying, togglePlay, playNext, playPrevious } = usePlayer();
  const audioRef = useRef(null);

  // 1. HLS 스트림 바인딩, 오디오 초기화 및 에러 예외 처리
  useEffect(() => {
    if (!currentTrack || currentTrack.type === 'YOUTUBE' || !currentTrack.streamUrl) return;

    const audio = audioRef.current;
    if (!audio) return;

    let hls = null;

    if (Hls.isSupported() && currentTrack.streamUrl.endsWith('.m3u8')) {
      hls = new Hls();
      hls.loadSource(currentTrack.streamUrl);
      hls.attachMedia(audio);

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (isPlaying) audio.play().catch(() => {});
      });

      // 🔴 [추가됨] HLS 네트워크/미디어 에러 발생 시 자동 복구 예외 처리
      hls.on(Hls.Events.ERROR, (event, data) => {
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              console.warn('네트워크 에러 발생, 스트리밍 재시도 중...');
              hls.startLoad();
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              console.warn('미디어 디코딩 에러 발생, 복구 시도 중...');
              hls.recoverMediaError();
              break;
            default:
              console.error('치명적인 에러 발생, 플레이어 중단');
              hls.destroy();
              break;
          }
        }
      });

      return () => {
        if (hls) {
          hls.destroy();
        }
      };
    } else if (audio.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari Native HLS 지원
      audio.src = currentTrack.streamUrl;
      if (isPlaying) audio.play().catch(() => {});
    } else {
      // 일반 오디오 URL
      audio.src = currentTrack.streamUrl;
      if (isPlaying) audio.play().catch(() => {});
    }
  }, [currentTrack]);

  // 2. 재생 / 일시정지 상태 동기화
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !currentTrack || currentTrack.type === 'YOUTUBE') return;

    if (isPlaying) {
      audio.play().catch(() => {});
    } else {
      audio.pause();
    }
  }, [isPlaying]);

  // 3. Notification / 백그라운드 미디어 컨트롤 (MediaSession API 확장)
  useEffect(() => {
    if (!('mediaSession' in navigator) || !currentTrack) return;

    navigator.mediaSession.metadata = new MediaMetadata({
      title: currentTrack.title || '재생 중인 곡',
      artist: currentTrack.artist || '알 수 없는 아티스트',
      artwork: [
        {
          src: currentTrack.thumbnailUrl || '/default-album.png',
          sizes: '512x512',
          type: 'image/jpeg',
        },
      ],
    });

    navigator.mediaSession.setActionHandler('play', () => togglePlay());
    navigator.mediaSession.setActionHandler('pause', () => togglePlay());

    // 🔴 [추가됨] 모바일 잠금 화면 및 브라우저 백그라운드 다음 곡 / 이전 곡 제어 연동
    if (playNext) {
      navigator.mediaSession.setActionHandler('nexttrack', () => playNext());
    }
    if (playPrevious) {
      navigator.mediaSession.setActionHandler('previoustrack', () => playPrevious());
    }
  }, [currentTrack, togglePlay, playNext, playPrevious]);

  return <audio ref={audioRef} style={{ display: 'none' }} />;
}