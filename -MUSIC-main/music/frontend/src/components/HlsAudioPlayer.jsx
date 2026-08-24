import React, { useEffect, useRef } from 'react';
import Hls from 'hls.js';
import { usePlayer } from '../context/PlayerContext';

export default function HlsAudioPlayer() {
  const { currentTrack, isPlaying, togglePlay } = usePlayer();
  const audioRef = useRef(null);

  // 1. HLS 스트림 바인딩 및 오디오 초기화
  useEffect(() => {
    if (!currentTrack || currentTrack.type === 'YOUTUBE' || !currentTrack.streamUrl) return;

    const audio = audioRef.current;
    if (!audio) return;

    if (Hls.isSupported() && currentTrack.streamUrl.endsWith('.m3u8')) {
      const hls = new Hls();
      hls.loadSource(currentTrack.streamUrl);
      hls.attachMedia(audio);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (isPlaying) audio.play().catch(() => {});
      });

      return () => {
        hls.destroy();
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

  // 3. Notification / 백그라운드 미디어 컨트롤 (MediaSession API)
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
  }, [currentTrack, togglePlay]);

  return <audio ref={audioRef} style={{ display: 'none' }} />;
}