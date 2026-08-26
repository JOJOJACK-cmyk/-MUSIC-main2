import api from './axiosInstance';

export const musicApi = {
  getAllMusics: () => api.get('/api/musics'),
  getMusicById: (id) => api.get(`/api/musics/${id}`),

  // 기존 직접 등록 API (필요 시 사용)
  createMusic: (musicData) => api.post('/api/musics', musicData),

  // YouTube Video ID 하나만 보내면 Spring Boot가 YouTube Data API에서
  // 제목/채널명/썸네일을 가져와 music 테이블에 저장한다.
  createMusicFromYouTube: (videoId) =>
    api.post('/api/musics/youtube', null, {
      params: { videoId },
    }),

  updateMusic: (id, musicData) => api.put(`/api/musics/${id}`, musicData),
  deleteMusic: (id) => api.delete(`/api/musics/${id}`),

  // 🔴 [추가] 실시간 TOP 100 차트/랭킹 데이터를 가져오는 API
  // (백엔드의 MusicSnapshotController 또는 랭킹 관련 엔드포인트 주소에 맞춤)
  getTop100Chart: () => api.get('/api/musics/ranking'),
};