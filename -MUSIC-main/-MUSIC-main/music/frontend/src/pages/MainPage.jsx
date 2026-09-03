import React, { useEffect, useState, useRef } from 'react';
import Header from '../components/Header';
import HeroBanner from '../components/HeroBanner';
import MusicCard from '../components/MusicCard';
import MusicModal from '../components/MusicModal';
import { musicApi } from '../api/musicApi';
import { usePlayer } from '../context/PlayerContext';
import axios from 'axios';

export default function MainPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [musics, setMusics] = useState([]);
  const [trending, setTrending] = useState([]); // 실시간 인기 급상승 곡 (청취기록 기반)
  const [searchTerm, setSearchTerm] = useState('');
  const [editingMusic, setEditingMusic] = useState(null);
  const [loading, setLoading] = useState(true);
  const [apiError, setApiError] = useState('');
  const [isAdmin, setIsAdmin] = useState(false);
  const { setPlaylist } = usePlayer();

  const scrollRef1 = useRef(null);
  const scrollRef2 = useRef(null);
  const scrollRef3 = useRef(null);
  const scrollRef4 = useRef(null);
  const scrollRef5 = useRef(null);

  const scroll = (ref, direction) => {
    if (ref.current) {
      const { scrollLeft, clientWidth } = ref.current;
      const scrollAmount = clientWidth * 0.75;

      ref.current.scrollTo({
        left: direction === 'left' ? scrollLeft - scrollAmount : scrollLeft + scrollAmount,
        behavior: 'smooth'
      });
    }
  };

  useEffect(() => {
    try {
      const rawUser = localStorage.getItem('user');
      if (rawUser) {
        const userObj = JSON.parse(rawUser);
        setIsAdmin(userObj && (userObj.role === 'ROLE_ADMIN' || userObj.role === 'ADMIN'));
      }
    } catch (e) {
      setIsAdmin(false);
    }
  }, []);

  const fetchMusics = async () => {
    setLoading(true);
    setApiError('');

    try {
      const musicRes = await axios.get('/api/musics').catch(() => ({ data: [] }));
      const musicItems = Array.isArray(musicRes.data) ? musicRes.data : [];

      let likedIds = new Set();
      try {
        const likedRes = await axios.get('/api/musics/liked', { withCredentials: true });
        likedIds = new Set((Array.isArray(likedRes.data) ? likedRes.data : []).map((m) => m.id));
      } catch (e) {}

      // 💡 클라이언트 방어 필터: 쇼츠/장편/비음악 영상 제외 (백엔드 필터 이중 안전장치, 90~480초)
      const NON_MUSIC = ['shorts', '쇼츠', '#shorts', 'lyrics', '가사', '해석', '발음', 'playlist',
        '플레이리스트', '모음', '메들리', '메드레이', 'medley', 'メドレー', 'mix', '믹스', '토크', '잡담',
        '클립', 'special clip', '스페셜 클립', '리뷰', '커버', 'cover', '노래방', 'mr', '1시간', '1 hour',
        'asmr', '직캠', '티저', 'teaser', '예고편',
        '라이브', '(live', 'live ver', 'live performance', 'live tour', 'arena tour', 'ライブ', 'ライヴ',
        'tour 20', 'cdtv', 'live 20', 'digest', '다이제스트', 'コール動画', 'メガパック', 'megapack',
        'mega pack', 'anniversary live', '페스티벌', 'festival', 'vlf', 'concert', '콘서트',
        '인기곡', '人気曲', 'ランキング', 'best of', 'compilation', '컴필레이션', 'top 40', 'top40', 'top 20',
        'greatest hits', 'greatest pop', 'pop hits', 'trending pop', 'spotify hits', 'chart hits',
        'billboard top', 'billboard songs', 'billboard hot', 'billboard hits', 'hits 20', 'mega hits',
        'hit songs', '히트곡', 'sing-along', 'sing along', 'grammy museum', 'the icon sessions'];
      // 쇼츠/장편/비음악 영상만 제외 (순서·개수는 서버 응답 그대로 유지 → "실시간 인기 급상승 곡" 원상)
      const isRealSong = (item) => {
        const title = item.title || '';
        const lower = title.toLowerCase();
        if (NON_MUSIC.some((kw) => lower.includes(kw))) return false;
        if ((title.match(/#/g) || []).length >= 3) return false;
        const d = item.durationSeconds;
        if (d != null && (d < 90 || d > 480)) return false;
        return true;
      };

      const KNOWN_GENRES = ['KPOP', 'JPOP', 'VTUBER', 'POP'];

      // 서버 장르 값을 대문자로 정규화 (순서는 그대로 두어 상위 10곡 = 인기 급상승 유지)
      const musicsWithLike = musicItems
        .filter(isRealSong)
        .map((item) => {
          const g = (item.genre || 'POP').toUpperCase();
          return {
            ...item,
            genre: KNOWN_GENRES.includes(g) ? g : 'POP',
            isLiked: likedIds.has(item.id),
          };
        });

      setMusics(musicsWithLike);
      setPlaylist(musicsWithLike);

      // 💡 "실시간 인기 급상승 곡" = DB 청취기록 기반 랭킹 (부족한 자리는 조회수 상위곡으로 채움)
      try {
        const chartRes = await axios.get('/api/chart/realtime');
        const chartItems = Array.isArray(chartRes.data) ? chartRes.data : [];
        const trendingTracks = chartItems.slice(0, 10).map((c) => ({
          id: c.id ?? c.musicId,
          youtubeVideoId: c.youtubeVideoId,
          title: c.title,
          artist: c.artist,
          thumbnailUrl: c.thumbnailUrl,
          listenCount: c.listenCount,
          isLiked: likedIds.has(c.id ?? c.musicId),
        }));
        const have = new Set(trendingTracks.map((t) => t.id));
        const filler = [...musicsWithLike]
          .sort((a, b) => (Number(b.viewCount) || 0) - (Number(a.viewCount) || 0))
          .filter((m) => !have.has(m.id));
        setTrending([...trendingTracks, ...filler].slice(0, 10));
      } catch (e) {
        setTrending(
          [...musicsWithLike]
            .sort((a, b) => (Number(b.viewCount) || 0) - (Number(a.viewCount) || 0))
            .slice(0, 10)
        );
      }
    } catch (err) {
      setMusics([]);
      setTrending([]);
      setPlaylist([]);
      setApiError('Spring Boot 서버 또는 /api/musics 연결을 확인해 주세요.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMusics();
  }, []);

  const handleToggleLike = async (musicId, nextLiked) => {
    try {
      await axios.post(`/api/musics/${musicId}/like`, {}, { withCredentials: true });
      setMusics((prev) => prev.map((m) => (m.id === musicId ? { ...m, isLiked: nextLiked } : m)));
    } catch (err) {
      alert('좋아요 처리에 실패했습니다.');
    }
  };

  const handleCreateOrUpdate = async (payload) => {
    try {
      if (editingMusic) {
        await musicApi.updateMusic(editingMusic.id, payload);
        alert('음원이 수정되었습니다.');
      } else {
        await musicApi.createMusicFromYouTube(payload.youtubeVideoId);
        alert('YouTube 음원이 DB에 등록되었습니다.');
      }
      setIsModalOpen(false);
      setEditingMusic(null);
      await fetchMusics();
    } catch (err) {
      alert('음원 등록에 실패했습니다.');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('정말 삭제하시겠습니까?')) return;
    try {
      await musicApi.deleteMusic(id);
      await fetchMusics();
      alert('삭제되었습니다.');
    } catch (err) {
      alert('삭제 중 오류가 발생했습니다.');
    }
  };

  const filteredMusics = musics.filter((m) => {
    const title = (m.title || '').toLowerCase();
    const artist = (m.artist || '').toLowerCase();
    const keyword = searchTerm.toLowerCase();
    return title.includes(keyword) || artist.includes(keyword);
  });

  // "실시간 인기 급상승 곡" = 청취기록 기반 랭킹 상위 10곡 (검색어로도 필터)
  const top10Musics = trending.filter((m) => {
    const kw = searchTerm.toLowerCase();
    return (m.title || '').toLowerCase().includes(kw) || (m.artist || '').toLowerCase().includes(kw);
  });

  // 카테고리 섹션 = "지금 유튜브에서 유행하는 곡" (공식 MV 위주)
  //  - "- Topic" 자동생성 채널(오디오 아트트랙) 제외 → 진짜 뮤직비디오만
  //  - 현재 인기차트 진입곡(trendingRank) 순위대로 먼저, 그 뒤 조회수 높은 순으로 채움
  //  - 같은 곡(제목) 중복·한 아티스트 몰아넣기(최대 3곡) 정리
  const normTitle = (t) =>
    (t || '')
      .toLowerCase()
      .replace(/\(.*?\)|\[.*?\]|【.*?】|feat\.?.*/g, '')
      .replace(/official|mv|m\/v|music video|audio|performance|video|visualizer/g, '')
      .replace(/[^a-z0-9가-힣ぁ-んァ-ン]/g, '')
      .trim();

  const isTopicChannel = (m) => /\s*-\s*topic\s*$/i.test(m.artist || '');

  const popularByGenre = (genre) => {
    const genreAll = filteredMusics.filter((m) => m.genre === genre);
    const nonTopic = genreAll.filter((m) => !isTopicChannel(m));
    // 공식 MV가 8곡 이상이면 Topic(오디오 아트트랙) 제외, 아니면 곡이 부족하므로 포함
    const inGenre = nonTopic.length >= 8 ? nonTopic : genreAll;
    const list = inGenre.slice().sort((a, b) => {
      const ar = a.trendingRank ?? 9999;
      const br = b.trendingRank ?? 9999;
      if (ar !== br) return ar - br;
      return (Number(b.viewCount) || 0) - (Number(a.viewCount) || 0);
    });

    const seenTitle = new Set();
    const perArtist = {};
    return list.filter((m) => {
      const nt = normTitle(m.title);
      if (nt && seenTitle.has(nt)) return false;
      const ak = (m.artist || '').toLowerCase().trim();
      perArtist[ak] = (perArtist[ak] || 0) + 1;
      if (perArtist[ak] > 3) return false;
      if (nt) seenTitle.add(nt);
      return true;
    });
  };

  const kpopMusics = popularByGenre('KPOP');
  const jpopMusics = popularByGenre('JPOP');
  const vtuberMusics = popularByGenre('VTUBER');
  const popMusics = popularByGenre('POP');

  return (
    <div className="home-page-container" style={{ width: '100%', paddingBottom: '40px' }}>
      <Header searchTerm={searchTerm} setSearchTerm={setSearchTerm} />
      <HeroBanner />

      {/* 실시간 인기 급상승 곡 */}
      <section className="content-section">
        <div className="section-header">
          <h2>✨ 실시간 인기 급상승 곡</h2>
          <span style={{ color: 'var(--text-sub)', fontSize: '14px' }}>Top 10</span>
        </div>
        <div className="scroll-container-wrapper">
          <button className="scroll-btn left" onClick={() => scroll(scrollRef1, 'left')}><i className="fa-solid fa-chevron-left"></i></button>
          <div className="card-grid-horizontal" ref={scrollRef1}>
            {top10Musics.map((music) => (
              <MusicCard key={music.id} music={music} isAdmin={isAdmin} onToggleLike={handleToggleLike} onEdit={(item) => { setEditingMusic(item); setIsModalOpen(true); }} onDelete={handleDelete} />
            ))}
          </div>
          <button className="scroll-btn right" onClick={() => scroll(scrollRef1, 'right')}><i className="fa-solid fa-chevron-right"></i></button>
        </div>
      </section>

      {/* K-POP 섹션 */}
      <section className="content-section" style={{ marginTop: '40px' }}>
        <div className="section-header">
          <h2>🔥 추천 K-POP 히트곡</h2>
          <span style={{ color: 'var(--text-sub)', fontSize: '14px' }}>{kpopMusics.length}곡</span>
        </div>
        <div className="scroll-container-wrapper">
          <button className="scroll-btn left" onClick={() => scroll(scrollRef2, 'left')}><i className="fa-solid fa-chevron-left"></i></button>
          <div className="card-grid-horizontal" ref={scrollRef2}>
            {kpopMusics.map((music) => (
              <MusicCard key={music.id} music={music} isAdmin={isAdmin} onToggleLike={handleToggleLike} onEdit={(item) => { setEditingMusic(item); setIsModalOpen(true); }} onDelete={handleDelete} />
            ))}
          </div>
          <button className="scroll-btn right" onClick={() => scroll(scrollRef2, 'right')}><i className="fa-solid fa-chevron-right"></i></button>
        </div>
      </section>

      {/* J-POP 섹션 */}
      <section className="content-section" style={{ marginTop: '40px' }}>
        <div className="section-header">
          <h2>🎧 감성 J-POP 플레이리스트</h2>
          <span style={{ color: 'var(--text-sub)', fontSize: '14px' }}>{jpopMusics.length}곡</span>
        </div>
        <div className="scroll-container-wrapper">
          <button className="scroll-btn left" onClick={() => scroll(scrollRef3, 'left')}><i className="fa-solid fa-chevron-left"></i></button>
          <div className="card-grid-horizontal" ref={scrollRef3}>
            {jpopMusics.map((music) => (
              <MusicCard key={music.id} music={music} isAdmin={isAdmin} onToggleLike={handleToggleLike} onEdit={(item) => { setEditingMusic(item); setIsModalOpen(true); }} onDelete={handleDelete} />
            ))}
          </div>
          <button className="scroll-btn right" onClick={() => scroll(scrollRef3, 'right')}><i className="fa-solid fa-chevron-right"></i></button>
        </div>
      </section>

      {/* 버튜버 섹션 */}
      <section className="content-section" style={{ marginTop: '40px' }}>
        <div className="section-header">
          <h2>⭐ 버튜버 & 버추얼 아이돌 스테이지</h2>
          <span style={{ color: 'var(--text-sub)', fontSize: '14px' }}>{vtuberMusics.length}곡</span>
        </div>
        <div className="scroll-container-wrapper">
          <button className="scroll-btn left" onClick={() => scroll(scrollRef5, 'left')}><i className="fa-solid fa-chevron-left"></i></button>
          <div className="card-grid-horizontal" ref={scrollRef5}>
            {vtuberMusics.map((music) => (
              <MusicCard key={music.id} music={music} isAdmin={isAdmin} onToggleLike={handleToggleLike} onEdit={(item) => { setEditingMusic(item); setIsModalOpen(true); }} onDelete={handleDelete} />
            ))}
          </div>
          <button className="scroll-btn right" onClick={() => scroll(scrollRef5, 'right')}><i className="fa-solid fa-chevron-right"></i></button>
        </div>
      </section>

      {/* POP 섹션 */}
      <section className="content-section" style={{ marginTop: '40px', marginBottom: '60px' }}>
        <div className="section-header">
          <h2>🌍 트렌디한 POP 글로벌 차트</h2>
          <span style={{ color: 'var(--text-sub)', fontSize: '14px' }}>{popMusics.length}곡</span>
        </div>
        <div className="scroll-container-wrapper">
          <button className="scroll-btn left" onClick={() => scroll(scrollRef4, 'left')}><i className="fa-solid fa-chevron-left"></i></button>
          <div className="card-grid-horizontal" ref={scrollRef4}>
            {popMusics.map((music) => (
              <MusicCard key={music.id} music={music} isAdmin={isAdmin} onToggleLike={handleToggleLike} onEdit={(item) => { setEditingMusic(item); setIsModalOpen(true); }} onDelete={handleDelete} />
            ))}
          </div>
          <button className="scroll-btn right" onClick={() => scroll(scrollRef4, 'right')}><i className="fa-solid fa-chevron-right"></i></button>
        </div>
      </section>

      <MusicModal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); setEditingMusic(null); }} onSubmit={handleCreateOrUpdate} initialData={editingMusic} />
    </div>
  );
}