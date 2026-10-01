import axios from 'axios';

const toTrack = (m) => ({
  id: m.id ?? m.musicId,
  youtubeVideoId: m.youtubeVideoId,
  title: m.title,
  artist: m.artist,
  thumbnailUrl: m.thumbnailUrl,
});

/**
 * 투표 1위 곡 제목으로 카탈로그에서 검색해, "지금 곡 끝나면 → 1위 곡 → 이후 실시간 인기곡"
 * 순서로 재생을 예약한다. LivePoll(사이트/OBS 독)과 전역 리스너(다른 페이지에 있을 때)가 공유한다.
 */
export async function resolveAndQueueWinner(songTitle, queueTrackThenList) {
  if (!songTitle) return;
  try {
    const [songRes, chartRes] = await Promise.all([
      axios.get('/api/musics/search', { params: { keyword: songTitle } }),
      axios.get('/api/chart/realtime').catch(() => ({ data: [] })),
    ]);
    const hit = (Array.isArray(songRes.data) ? songRes.data : []).find((m) => m.youtubeVideoId);
    if (!hit) return;
    const chart = (Array.isArray(chartRes.data) ? chartRes.data : [])
      .map(toTrack)
      .filter((t) => t.youtubeVideoId);
    queueTrackThenList(hit, chart);
  } catch (_) {}
}
