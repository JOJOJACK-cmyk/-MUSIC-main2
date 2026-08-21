import React from 'react';
import { usePlayer } from '../context/PlayerContext';

export default function MusicCard({ music, onEdit, onDelete }) {
  const { playTrack, currentTrack, isPlaying } = usePlayer();
  const isCurrent = currentTrack?.id === music.id;

  return (
    <div className="music-card" onClick={() => playTrack(music)}>
      <div className="card-img">
        {music.thumbnailUrl ? (
          <img src={music.thumbnailUrl} alt={music.title} />
        ) : (
          <i className="fa-solid fa-music"></i>
        )}
        <button
          className="play-btn-hover"
          onClick={(e) => {
            e.stopPropagation();
            playTrack(music);
          }}
        >
          <i className={`fa-solid ${isCurrent && isPlaying ? 'fa-pause' : 'fa-play'}`}></i>
        </button>
      </div>
      <h4>{music.title}</h4>
      <p>{music.artist || '아티스트 미상'}</p>

      <div className="card-actions" onClick={(e) => e.stopPropagation()}>
        {onEdit && (
          <button onClick={() => onEdit(music)}>
            <i className="fa-solid fa-pen-to-square"></i>
          </button>
        )}
        {onDelete && (
          <button onClick={() => onDelete(music.id)}>
            <i className="fa-solid fa-trash"></i>
          </button>
        )}
      </div>
    </div>
  );
}
