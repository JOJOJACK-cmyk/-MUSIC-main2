import React, { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';

export default function MusicPlayer({ currentTrack, userId = 1 }) {
  const audioRef = useRef(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [listenSeconds, setListenSeconds] = useState(0);
  const [hasLogged, setHasLogged] = useState(false);

  // 1. HLS 스트리밍 오디오 연결 & 트랙 변경 초기화
  useEffect(() => {
    if (!currentTrack || currentTrack.type === 'YOUTUBE') return;

    const audio = audioRef.current;
    if (!audio) return;

    setListenSeconds(0);
    setHasLogged(false);

    // HLS.js 지원 브라우저 (Chrome, Firefox, Safari 데스크톱 등)
    if (Hls.isSupported() && currentTrack.streamUrl?.endsWith('.m3u8')) {
      const hls = new Hls();
      hls.loadSource(currentTrack.streamUrl);
      hls.attachMedia(audio);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        audio.play().catch(() => setIsPlaying(false));
      });

      return () => {
        hls.destroy();
      };
    } else if (audio.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari Native HLS 지원
      audio.src = currentTrack.streamUrl;
      audio.play().catch(() => setIsPlaying(false));
    } else {
      // 일반 MP3/오디오 소스
      audio.src = currentTrack.streamUrl;
      audio.play().catch(() => setIsPlaying(false));
    }
  }, [currentTrack]);

  // 2. 30초 청취 시 백엔드 로그 수집 API 자동 전송 (Log Collector Trigger)
  useEffect(() => {
    let timer = null;
    if (isPlaying && !hasLogged) {
      timer = setInterval(() => {
        setListenSeconds((prev) => {
          const next = prev + 1;
          if (next >= 30) {
            sendListenLog(currentTrack.id, 30);
            setHasLogged(true);
          }
          return next;
        });
      }, 1000);
    }

    return () => {
      if (timer) clearInterval(timer);
    };
  }, [isPlaying, hasLogged, currentTrack]);

  const sendListenLog = async (musicId, seconds) => {
    try {
      await fetch('/api/v1/logs/listen', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId: userId,
          musicId: musicId,
          listenSeconds: seconds,
        }),
      });
      console.log('30초 청취 로그가 성공적으로 기록되었습니다.');
    } catch (err) {
      console.error('청취 로그 전송 실패:', err);
    }
  };

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

    navigator.mediaSession.setActionHandler('play', () => {
      if (audioRef.current) {
        audioRef.current.play();
        setIsPlaying(true);
      }
    });

    navigator.mediaSession.setActionHandler('pause', () => {
      if (audioRef.current) {
        audioRef.current.pause();
        setIsPlaying(false);
      }
    });

    navigator.mediaSession.setActionHandler('seekto', (details) => {
      if (audioRef.current && details.seekTime) {
        audioRef.current.currentTime = details.seekTime;
      }
    });
  }, [currentTrack]);

  const togglePlay = () => {
    if (!audioRef.current) return;
    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
    } else {
      audioRef.current.play();
      setIsPlaying(true);
    }
  };

  if (!currentTrack) {
    return <div className="p-4 text-gray-500">선택된 음원이 없습니다.</div>;
  }

  return (
    <div className="fixed bottom-0 left-0 right-0 bg-gray-900 text-white p-4 shadow-xl flex items-center justify-between z-50">
      {/* 트랙 메타데이터 표시 */}
      <div className="flex items-center gap-4">
        <img
          src={currentTrack.thumbnailUrl || 'https://via.placeholder.com/60'}
          alt={currentTrack.title}
          className="w-14 h-14 rounded-md object-cover"
        />
        <div>
          <h4 className="font-semibold text-base">{currentTrack.title}</h4>
          <p className="text-gray-400 text-sm">{currentTrack.artist}</p>
        </div>
      </div>

      {/* 컨트롤 영역 */}
      <div className="flex flex-col items-center gap-1">
        {currentTrack.type === 'YOUTUBE' ? (
          /* 유튜브 IFrame 플레이어 임베드 */
          <div className="w-[300px] h-[80px] overflow-hidden rounded">
            <iframe
              width="100%"
              height="80"
              src={`https://www.youtube.com/embed/${currentTrack.youtubeVideoId}?autoplay=1&enablejsapi=1`}
              title="YouTube audio player"
              frameBorder="0"
              allow="autoplay; encrypted-media"
              allowFullScreen
            />
          </div>
        ) : (
          /* HLS/HTML5 Audio 컨트롤 */
          <>
            <audio
              ref={audioRef}
              onPlay={() => setIsPlaying(true)}
              onPause={() => setIsPlaying(false)}
            />
            <button
              onClick={togglePlay}
              className="bg-indigo-600 hover:bg-indigo-500 px-6 py-2 rounded-full font-bold shadow transition"
            >
              {isPlaying ? '일시정지' : '재생'}
            </button>
            <span className="text-xs text-gray-400">
              청취 시간: {listenSeconds}s {hasLogged && '✔ (수집완료)'}
            </span>
          </>
        )}
      </div>

      <div className="w-24 text-right text-xs text-gray-400">
        {currentTrack.type === 'YOUTUBE' ? 'YouTube 재생' : 'HLS 스트리밍'}
      </div>
    </div>
  );
}