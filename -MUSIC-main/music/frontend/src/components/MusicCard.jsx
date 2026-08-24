import React from 'react';
import { usePlayer } from '../context/PlayerContext';

export default function MusicCard({
  music,
  onEdit,
  onDelete
}) {

  const {
    playTrack,
    togglePlay,
    currentTrack,
    isPlaying
  } = usePlayer();


  // 지금 이 카드의 음악이 현재 재생 중인 음악인지 확인
  const isCurrent =
    currentTrack?.id === music.id;


  // 음악 카드 재생 버튼
  const handlePlay = () => {

    // 현재 선택되어 있는 곡을 다시 눌렀다면
    if (isCurrent) {

      // 처음부터 다시 재생하지 않고
      // 재생 ↔ 일시정지
      togglePlay();

      return;
    }


    // 다른 곡을 눌렀다면
    // 새로운 곡 재생
    playTrack(music);
  };


  return (

    <div
      className="music-card"
      onClick={handlePlay}
    >

      <div className="card-img">

        {music.thumbnailUrl ? (

          <img
            src={music.thumbnailUrl}
            alt={music.title}
          />

        ) : (

          <i className="fa-solid fa-music"></i>

        )}


        {/* 카드 가운데 재생 버튼 */}
        <button
          className="play-btn-hover"

          onClick={(e) => {

            // 부모 music-card 클릭까지
            // 동시에 실행되는 것을 방지
            e.stopPropagation();

            handlePlay();
          }}
        >

          <i
            className={
              `fa-solid ${
                isCurrent && isPlaying
                  ? 'fa-pause'
                  : 'fa-play'
              }`
            }
          ></i>

        </button>

      </div>


      {/* 곡 제목 */}
      <h4>
        {music.title}
      </h4>


      {/* 아티스트 */}
      <p>
        {music.artist || '아티스트 미상'}
      </p>


      {/* 수정 / 삭제 */}
      <div
        className="card-actions"

        onClick={(e) =>
          e.stopPropagation()
        }
      >

        {onEdit && (

          <button
            onClick={() =>
              onEdit(music)
            }
          >

            <i className="fa-solid fa-pen-to-square"></i>

          </button>

        )}


        {onDelete && (

          <button
            onClick={() =>
              onDelete(music.id)
            }
          >

            <i className="fa-solid fa-trash"></i>

          </button>

        )}

      </div>

    </div>
  );
}