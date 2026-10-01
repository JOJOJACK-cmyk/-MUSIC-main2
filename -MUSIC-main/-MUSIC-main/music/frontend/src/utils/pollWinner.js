import axios from 'axios';

const toTrack = (m) => ({
  id: m.id ?? m.musicId,
  youtubeVideoId: m.youtubeVideoId,
  title: m.title,
  artist: m.artist,
  thumbnailUrl: m.thumbnailUrl,
});

/**
 * 투표 1위 곡을 "지금 곡 끝나면 → 1위 곡 → 이후 실시간 인기곡" 순서로 재생 예약한다.
 *  - winner.musicId 가 있으면(스트리머가 카탈로그에서 고른 곡) 그 곡을 바로 가져온다.
 *  - 없으면(직접 입력한 제목) 제목으로 카탈로그를 검색해 첫 결과를 쓴다.
 * 전역 리스너(BroadcasterNextSongListener)가 사용한다.
 *
 * @param {{songTitle?: string, musicId?: number}} winner
 */
export async function resolveAndQueueWinner(winner, queueTrackThenList) {
  const songTitle = winner?.songTitle;
  const musicId = winner?.musicId;
  if (!songTitle && !musicId) return;
  try {
    const findHit = async () => {
      if (musicId) {
        try {
          const r = await axios.get(`/api/musics/${musicId}`);
          if (r.data?.youtubeVideoId) return r.data;
        } catch (_) {
          // 곡이 삭제된 경우 등 → 제목 검색으로 폴백
        }
      }
      if (!songTitle) return null;
      const r = await axios.get('/api/musics/search', { params: { keyword: songTitle } });
      return (Array.isArray(r.data) ? r.data : []).find((m) => m.youtubeVideoId) || null;
    };

    const [hit, chartRes] = await Promise.all([
      findHit(),
      axios.get('/api/chart/realtime').catch(() => ({ data: [] })),
    ]);
    if (!hit) return;
    const chart = (Array.isArray(chartRes.data) ? chartRes.data : [])
      .map(toTrack)
      .filter((t) => t.youtubeVideoId);
    queueTrackThenList(toTrack(hit), chart);
  } catch (_) {}
}
