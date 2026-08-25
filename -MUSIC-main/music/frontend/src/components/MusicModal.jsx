import React, { useEffect, useState } from 'react';

function extractYouTubeVideoId(value) {
  const input = value.trim();
  if (!input) return '';

  // 이미 Video ID만 입력한 경우
  if (/^[a-zA-Z0-9_-]{11}$/.test(input)) {
    return input;
  }

  try {
    const url = new URL(input);

    if (url.hostname.includes('youtu.be')) {
      return url.pathname.split('/').filter(Boolean)[0] || '';
    }

    if (url.hostname.includes('youtube.com')) {
      if (url.pathname === '/watch') {
        return url.searchParams.get('v') || '';
      }

      const parts = url.pathname.split('/').filter(Boolean);
      if (['shorts', 'embed', 'live'].includes(parts[0])) {
        return parts[1] || '';
      }
    }
  } catch (_) {
    return '';
  }

  return '';
}

export default function MusicModal({ isOpen, onClose, onSubmit, initialData }) {
  const [youtubeInput, setYoutubeInput] = useState('');
  const [formData, setFormData] = useState({
    title: '',
    artist: '',
    thumbnailUrl: '',
  });
  const [validationMessage, setValidationMessage] = useState('');

  useEffect(() => {
    setValidationMessage('');

    if (initialData) {
      // 카멜 케이스(thumbnailUrl)와 스네이크 케이스(thumbnail_url) 모두 대응
      const thumbnail = initialData.thumbnailUrl || initialData.thumbnail_url || '';
      const videoId = initialData.youtubeVideoId || initialData.youtube_video_id || '';

      setFormData({
        title: initialData.title || '',
        artist: initialData.artist || '',
        thumbnailUrl: thumbnail,
      });
      setYoutubeInput(videoId);
    } else {
      setFormData({ title: '', artist: '', thumbnailUrl: '' });
      setYoutubeInput('');
    }
  }, [initialData, isOpen]);

  if (!isOpen) return null;

  const handleSubmit = (e) => {
    e.preventDefault();

    if (initialData) {
      // 백엔드 DTO 규격(카멜/스네이크)에 맞춰 둘 다 실어서 전송
      onSubmit({
        ...formData,
        thumbnail_url: formData.thumbnailUrl,
        youtubeVideoId: youtubeInput,
        youtube_video_id: youtubeInput
      });
      return;
    }

    const videoId = extractYouTubeVideoId(youtubeInput);
    if (!videoId) {
      setValidationMessage('올바른 YouTube URL 또는 11자리 Video ID를 입력해 주세요.');
      return;
    }

    onSubmit({ youtubeVideoId: videoId });
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{initialData ? '음원 수정' : 'YouTube 음원 등록'}</h3>
          <button className="modal-close-btn" onClick={onClose}>
            <i className="fa-solid fa-xmark"></i>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          {initialData ? (
            <>
              <div className="input-group">
                <i className="fa-solid fa-music"></i>
                <input
                  type="text"
                  placeholder="곡 제목"
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  required
                />
              </div>

              <div className="input-group">
                <i className="fa-solid fa-user"></i>
                <input
                  type="text"
                  placeholder="아티스트"
                  value={formData.artist}
                  onChange={(e) => setFormData({ ...formData, artist: e.target.value })}
                />
              </div>

              <div className="input-group">
                <i className="fa-solid fa-image"></i>
                <input
                  type="url"
                  placeholder="썸네일 이미지 URL"
                  value={formData.thumbnailUrl}
                  onChange={(e) => setFormData({ ...formData, thumbnailUrl: e.target.value })}
                />
              </div>

              <div className="input-group">
                <i className="fa-brands fa-youtube"></i>
                <input type="text" value={youtubeInput} disabled />
              </div>
            </>
          ) : (
            <>
              <div className="input-group">
                <i className="fa-brands fa-youtube"></i>
                <input
                  type="text"
                  placeholder="YouTube URL 또는 Video ID"
                  value={youtubeInput}
                  onChange={(e) => {
                    setYoutubeInput(e.target.value);
                    setValidationMessage('');
                  }}
                  required
                />
              </div>
              <p style={{ color: 'var(--text-sub)', fontSize: '13px', lineHeight: 1.5 }}>
                Video ID만 입력하면 Spring Boot가 YouTube API에서 제목, 채널명, 썸네일을 자동으로 가져옵니다.
              </p>
              {validationMessage && (
                <p style={{ color: '#ff6b6b', fontSize: '13px' }}>{validationMessage}</p>
              )}
            </>
          )}

          <button type="submit" className="auth-submit-btn">
            {initialData ? '수정 완료' : 'YouTube에서 등록'}
          </button>
        </form>
      </div>
    </div>
  );
}