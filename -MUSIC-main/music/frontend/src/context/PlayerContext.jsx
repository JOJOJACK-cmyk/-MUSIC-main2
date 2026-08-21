import React, { createContext, useContext, useState, useEffect } from 'react';

const PlayerContext = createContext(null);

export const PlayerProvider = ({ children }) => {
  const [currentTrack, setCurrentTrack] = useState(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(210); // 3:30 default
  const [volume, setVolume] = useState(80);
  const [playlist, setPlaylist] = useState([]);
  const [isShuffle, setIsShuffle] = useState(false);
  const [isRepeat, setIsRepeat] = useState(false);

  // Simulated playback timer for smooth progress
  useEffect(() => {
    let interval = null;
    if (isPlaying) {
      interval = setInterval(() => {
        setCurrentTime((prev) => {
          if (prev >= duration) {
            handleNextTrack();
            return 0;
          }
          return prev + 1;
        });
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [isPlaying, duration, playlist, currentTrack]);

  const playTrack = (track) => {
    setCurrentTrack(track);
    setIsPlaying(true);
    setCurrentTime(0);
  };

  const togglePlay = () => {
    if (!currentTrack && playlist.length > 0) {
      playTrack(playlist[0]);
      return;
    }
    setIsPlaying((prev) => !prev);
  };

  const handleNextTrack = () => {
    if (playlist.length === 0) return;
    const currentIndex = playlist.findIndex((t) => t.id === currentTrack?.id);
    if (currentIndex === -1) {
      playTrack(playlist[0]);
      return;
    }
    if (isShuffle) {
      const randomIndex = Math.floor(Math.random() * playlist.length);
      playTrack(playlist[randomIndex]);
    } else {
      const nextIndex = (currentIndex + 1) % playlist.length;
      playTrack(playlist[nextIndex]);
    }
  };

  const handlePrevTrack = () => {
    if (playlist.length === 0) return;
    const currentIndex = playlist.findIndex((t) => t.id === currentTrack?.id);
    if (currentIndex === -1 || currentIndex === 0) {
      playTrack(playlist[playlist.length - 1]);
    } else {
      playTrack(playlist[currentIndex - 1]);
    }
  };

  const seekTime = (percentage) => {
    setCurrentTime(Math.floor((percentage / 100) * duration));
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
        setVolume,
        setIsShuffle,
        setIsRepeat,
      }}
    >
      {children}
    </PlayerContext.Provider>
  );
};

export const usePlayer = () => useContext(PlayerContext);
